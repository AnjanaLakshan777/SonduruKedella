package com.sondurukedella.suppliers.service;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.agreements.storage.AgreementFileStore;
import com.sondurukedella.agreements.storage.AgreementStorageProperties;
import com.sondurukedella.agreements.storage.StoredFile;
import com.sondurukedella.agreements.validation.AgreementFiles;
import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.StorageException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.inventory.dto.InvoiceLineReceipt;
import com.sondurukedella.inventory.dto.MaterialResponse;
import com.sondurukedella.inventory.service.MaterialService;
import com.sondurukedella.inventory.service.StockMovementService;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.settings.service.MeasurementUnitService;
import com.sondurukedella.settings.service.PaymentMethodService;
import com.sondurukedella.suppliers.dto.SupplierInvoiceCancelRequest;
import com.sondurukedella.suppliers.dto.SupplierInvoiceCreateRequest;
import com.sondurukedella.suppliers.dto.SupplierInvoiceDetail;
import com.sondurukedella.suppliers.dto.SupplierInvoiceFilter;
import com.sondurukedella.suppliers.dto.SupplierInvoiceResponse;
import com.sondurukedella.suppliers.dto.SupplierInvoiceResponse.InvoiceStatus;
import com.sondurukedella.suppliers.dto.SupplierInvoiceSummary;
import com.sondurukedella.suppliers.dto.SupplierInvoiceUpdateRequest;
import com.sondurukedella.suppliers.dto.SupplierPaymentRequest;
import com.sondurukedella.suppliers.dto.SupplierPaymentResponse;
import com.sondurukedella.suppliers.dto.SupplierPaymentVoidRequest;
import com.sondurukedella.suppliers.dto.SupplierResponse;
import com.sondurukedella.suppliers.entity.InvoiceAllocation;
import com.sondurukedella.suppliers.entity.SupplierInvoice;
import com.sondurukedella.suppliers.entity.SupplierInvoiceLine;
import com.sondurukedella.suppliers.entity.SupplierPayment;
import com.sondurukedella.suppliers.repository.SupplierInvoiceLineRepository;
import com.sondurukedella.suppliers.repository.SupplierInvoiceRepository;
import com.sondurukedella.suppliers.repository.SupplierPaymentRepository;
import com.sondurukedella.suppliers.validation.SupplierText;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// Supplier invoices and payments.
// Stock invoice lines go into stock as FIFO receipts in the same transaction - they only become a cost
// when issued. Project invoices are a cost for that project straight away, no stock involved.
// Payments lock the invoice, use requestId so a resend is harmless, and are voided rather than deleted.
@Service
@RequiredArgsConstructor
public class SupplierInvoiceService {

    public static final int MAX_PAGE_SIZE = 100;

    private final SupplierInvoiceRepository invoices;
    private final SupplierInvoiceLineRepository lines;
    private final SupplierPaymentRepository payments;
    private final SupplierService supplierService;
    private final ProjectService projectService;
    private final MaterialService materialService;
    private final StockMovementService stockService;
    private final MeasurementUnitService units;
    private final PaymentMethodService paymentMethods;
    private final ActivityService activityService;
    private final AgreementFileStore fileStore;
    private final AgreementStorageProperties storageProperties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Transactional
    public SupplierInvoiceDetail create(SupplierInvoiceCreateRequest request) {
        SupplierResponse supplier = supplierService.find(request.supplierId())
                .orElseThrow(() -> new InvalidRequestException("The selected supplier does not exist."));
        if (!supplier.active()) {
            throw new InvalidRequestException(supplier.name() + " is inactive and can't take new invoices.");
        }
        String invoiceNo = request.invoiceNo().strip();
        if (invoices.existsBySupplierIdAndInvoiceNoAndCancelledAtIsNull(supplier.id(), invoiceNo)) {
            throw new ConflictException("Invoice " + invoiceNo + " from " + supplier.name() + " is already recorded.");
        }
        Long projectId = project(request);
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : request.invoiceDate().plusDays(supplier.creditDays());
        if (dueDate.isBefore(request.invoiceDate())) {
            throw new InvalidRequestException("The due date can't be before the invoice date.");
        }

        List<PlannedLine> planned = new ArrayList<>();
        for (SupplierInvoiceCreateRequest.Line line : request.lines()) {
            planned.add(plan(request.allocation(), line, planned.size() + 1));
        }
        BigDecimal total = Money.amount(planned.stream().map(PlannedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));

        SupplierInvoice invoice = invoices.saveAndFlush(new SupplierInvoice(supplier.id(), invoiceNo, request.invoiceDate(), dueDate,
                request.allocation(), projectId, total, SupplierText.trimToNull(request.notes())));
        for (PlannedLine p : planned) {
            SupplierInvoiceLine saved = lines.saveAndFlush(new SupplierInvoiceLine(invoice.getId(), invoice.getAllocation(), p.lineNo(),
                    p.materialId(), p.description(), p.quantity(), p.unitId(), p.unitPrice(), p.amount()));
            if (invoice.getAllocation() == InvoiceAllocation.STOCK) {
                stockService.receiveInvoiceLine(new InvoiceLineReceipt(saved.getId(), p.materialId(), p.unitId(), invoice.getInvoiceDate(),
                        p.quantity(), p.unitPrice(), supplier.id(), invoiceNo));
            }
        }
        activityService.record(projectId, ActivityKind.EXPENSE, "Supplier invoice " + invoiceNo + " from " + supplier.name() + ": "
                + Money.format(total) + (projectId == null ? " (into stock)." : " (delivered to the project)."));
        if (request.initialPayment() != null) {
            recordOn(invoice, request.initialPayment());
        }
        return detail(invoice);
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceDetail get(long id) {
        return detail(invoices.findById(id).orElseThrow(SupplierInvoiceService::notFound));
    }

    // Due date/notes always. Everything else only on an unpaid project invoice - a stock invoice is already
    // in the ledger. Locked like payments so an edit and a payment can't cross.
    @Transactional
    public SupplierInvoiceDetail update(long id, SupplierInvoiceUpdateRequest request) {
        SupplierInvoice invoice = invoices.findByIdForUpdate(id).orElseThrow(SupplierInvoiceService::notFound);
        if (invoice.isCancelled()) {
            throw new BusinessRuleException("A cancelled invoice can't be edited.");
        }
        ConflictException.requireCurrentVersion(request.version(), invoice.getVersion());
        if (request.dueDate().isBefore(request.invoiceDate())) {
            throw new InvalidRequestException("The due date can't be before the invoice date.");
        }
        String invoiceNo = request.invoiceNo().strip();
        boolean termsLocked = invoice.getAllocation() == InvoiceAllocation.STOCK || payments.sumActive(id).signum() > 0;
        List<PlannedLine> planned = new ArrayList<>();
        for (SupplierInvoiceCreateRequest.Line line : request.lines()) {
            planned.add(plan(invoice.getAllocation(), line, planned.size() + 1));
        }
        if (termsLocked) {
            if (!sameTerms(invoice, invoiceNo, request.invoiceDate(), request.projectId(), planned)) {
                throw new BusinessRuleException(invoice.getAllocation() == InvoiceAllocation.STOCK
                        ? "This invoice is already in stock: only the due date and notes can change."
                        : "This invoice has payments: only the due date and notes can change. Void the payments first.");
            }
        } else {
            if (invoices.existsBySupplierIdAndInvoiceNoAndCancelledAtIsNullAndIdNot(invoice.getSupplierId(), invoiceNo, id)) {
                throw new ConflictException("Invoice " + invoiceNo + " from this supplier is already recorded.");
            }
            Long projectId = project(new SupplierInvoiceCreateRequest(invoice.getSupplierId(), invoiceNo, request.invoiceDate(), null,
                    InvoiceAllocation.PROJECT, request.projectId(), null, request.lines(), null));
            BigDecimal total = Money.amount(planned.stream().map(PlannedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
            lines.deleteByInvoiceId(id);
            lines.flush();
            for (PlannedLine p : planned) {
                lines.save(new SupplierInvoiceLine(id, invoice.getAllocation(), p.lineNo(), p.materialId(), p.description(), p.quantity(),
                        p.unitId(), p.unitPrice(), p.amount()));
            }
            invoice.replaceTerms(invoiceNo, request.invoiceDate(), projectId, total);
        }
        invoice.editNotes(request.dueDate(), SupplierText.trimToNull(request.notes()));
        invoice = invoices.saveAndFlush(invoice);
        activityService.record(invoice.getProjectId(), ActivityKind.EXPENSE, "Supplier invoice " + invoice.getInvoiceNo() + " edited.");
        return detail(invoice);
    }

    // only unpaid project invoices; the row stays and the number can be used again
    @Transactional
    public SupplierInvoiceDetail cancel(long id, SupplierInvoiceCancelRequest request) {
        SupplierInvoice invoice = invoices.findByIdForUpdate(id).orElseThrow(SupplierInvoiceService::notFound);
        if (invoice.isCancelled()) {
            throw new ConflictException("This invoice was already cancelled.");
        }
        if (invoice.getAllocation() == InvoiceAllocation.STOCK) {
            throw new BusinessRuleException("A stock invoice is already in the stock ledger and can't be cancelled; correct the stock with an adjustment.");
        }
        ConflictException.requireCurrentVersion(request.version(), invoice.getVersion());
        if (payments.sumActive(id).signum() > 0) {
            throw new BusinessRuleException("Void the invoice's payments first.");
        }
        invoice.cancel(OffsetDateTime.now(clock), request.reason().strip());
        invoice = invoices.saveAndFlush(invoice);
        activityService.record(invoice.getProjectId(), ActivityKind.EXPENSE, "Supplier invoice " + invoice.getInvoiceNo() + " cancelled: "
                + invoice.getCancelReason());
        return detail(invoice);
    }

    // Same private store and checks as agreements. File first, then the row; the old file is deleted
    // once the new one is saved.
    public SupplierInvoiceDetail attach(long id, MultipartFile file) {
        if (!invoices.existsById(id)) {
            throw notFound();
        }
        long maxBytes = storageProperties.maxSize().toBytes();
        String fileName = AgreementFiles.safeFileName(file.getOriginalFilename());
        AgreementFiles.validateUpload(fileName, file.getContentType(), file.getSize(), maxBytes);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStore.store(in, maxBytes);
        } catch (IOException ex) {
            throw new StorageException("Could not read the uploaded file", ex);
        }
        String replaced;
        try {
            replaced = transactions.execute(status -> {
                SupplierInvoice invoice = invoices.findByIdForUpdate(id).orElseThrow(SupplierInvoiceService::notFound);
                String old = invoice.getAttachmentStorageKey();
                invoice.attach(stored.key(), fileName, stored.size());
                invoices.saveAndFlush(invoice);
                return old;
            });
        } catch (RuntimeException ex) {
            fileStore.deleteQuietly(stored.key());
            throw ex;
        }
        if (replaced != null) {
            fileStore.deleteQuietly(replaced);
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload attachment(long id) {
        SupplierInvoice invoice = invoices.findById(id).orElseThrow(SupplierInvoiceService::notFound);
        if (invoice.getAttachmentStorageKey() == null) {
            throw new NotFoundException("No PDF is attached to this invoice.");
        }
        if (!fileStore.exists(invoice.getAttachmentStorageKey())) {
            throw new StorageException("Supplier invoice " + id + " has attachment metadata but no stored file", null);
        }
        return new AttachmentDownload(invoice.getAttachmentFilename(), invoice.getAttachmentSize(), fileStore.open(invoice.getAttachmentStorageKey()));
    }

    // caller closes the stream (Spring does it when writing the response)
    public record AttachmentDownload(String fileName, long size, InputStream content) {
    }

    private boolean sameTerms(SupplierInvoice invoice, String invoiceNo, LocalDate invoiceDate, Long projectId, List<PlannedLine> planned) {
        List<SupplierInvoiceLine> current = lines.findByInvoiceIdOrderByLineNo(invoice.getId());
        if (!invoice.getInvoiceNo().equals(invoiceNo) || !invoice.getInvoiceDate().equals(invoiceDate)
                || !java.util.Objects.equals(invoice.getProjectId(), projectId) || current.size() != planned.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            SupplierInvoiceLine c = current.get(i);
            PlannedLine p = planned.get(i);
            if (!java.util.Objects.equals(c.getMaterialId(), p.materialId()) || !c.getDescription().equals(p.description())
                    || c.getQuantity().compareTo(p.quantity()) != 0 || c.getUnitId() != p.unitId() || c.getUnitPrice().compareTo(p.unitPrice()) != 0) {
                return false;
            }
        }
        return true;
    }

    // status needs the paid totals so it's filtered in memory - fine for this many invoices
    @Transactional(readOnly = true)
    public PageResponse<SupplierInvoiceResponse> search(SupplierInvoiceFilter filter, Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        if (pageable.getSort().isSorted()) {
            throw new InvalidRequestException("Supplier invoices are always listed newest first.");
        }
        List<SupplierInvoiceResponse> rows = rows(filter);
        int size = pageable.getPageSize();
        int from = (int) Math.min((long) pageable.getPageNumber() * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new PageResponse<>(rows.subList(from, to), pageable.getPageNumber(), size, rows.size(), (rows.size() + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceSummary summary(SupplierInvoiceFilter filter) {
        List<SupplierInvoiceResponse> rows = rows(filter).stream().filter(r -> r.status() != InvoiceStatus.CANCELLED).toList();
        return new SupplierInvoiceSummary(rows.size(), sum(rows, SupplierInvoiceResponse::total),
                sum(rows.stream().filter(r -> r.allocation() == InvoiceAllocation.STOCK).toList(), SupplierInvoiceResponse::total),
                sum(rows.stream().filter(r -> r.allocation() == InvoiceAllocation.PROJECT).toList(), SupplierInvoiceResponse::total),
                sum(rows, SupplierInvoiceResponse::paid), sum(rows, SupplierInvoiceResponse::outstanding),
                sum(rows.stream().filter(r -> r.status() == InvoiceStatus.OVERDUE).toList(), SupplierInvoiceResponse::outstanding));
    }

    // used by expenses to warn about entering the same purchase twice
    @Transactional(readOnly = true)
    public boolean existsActive(long supplierId, String invoiceNo) {
        return invoices.existsBySupplierIdAndInvoiceNoAndCancelledAtIsNull(supplierId, invoiceNo.strip());
    }

    // used by the reports (project material cost)
    @Transactional(readOnly = true)
    public List<SupplierInvoiceResponse> forProject(long projectId) {
        return rows(new SupplierInvoiceFilter(null, projectId, InvoiceAllocation.PROJECT, null, null, null)).stream()
                .filter(r -> r.status() != InvoiceStatus.CANCELLED).toList();
    }

    @Transactional
    public PaymentResult recordPayment(long invoiceId, SupplierPaymentRequest request) {
        return recordOn(invoices.findByIdForUpdate(invoiceId).orElseThrow(SupplierInvoiceService::notFound), request);
    }

    // the row stays, it just stops counting
    @Transactional
    public SupplierPaymentResponse voidPayment(long invoiceId, long paymentId, SupplierPaymentVoidRequest request) {
        SupplierInvoice invoice = invoices.findByIdForUpdate(invoiceId).orElseThrow(SupplierInvoiceService::notFound);
        SupplierPayment payment = payments.findById(paymentId).filter(p -> p.getInvoiceId() == invoiceId)
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        if (payment.isVoided()) {
            throw new ConflictException("This payment was already voided.");
        }
        ConflictException.requireCurrentVersion(request.version(), payment.getVersion());
        payment.markVoided(OffsetDateTime.now(clock), request.reason().strip());
        payment = payments.saveAndFlush(payment);
        activityService.record(invoice.getProjectId(), ActivityKind.EXPENSE, "Supplier payment of " + Money.format(payment.getAmount())
                + " on invoice " + invoice.getInvoiceNo() + " voided: " + payment.getVoidReason());
        return toResponse(payment, methodName(payment.getPaymentMethodId()));
    }

    @Transactional(readOnly = true)
    public List<SupplierPaymentResponse> listPayments(long invoiceId) {
        if (!invoices.existsById(invoiceId)) {
            throw notFound();
        }
        return paymentViews(invoiceId);
    }

    private PaymentResult recordOn(SupplierInvoice invoice, SupplierPaymentRequest request) {
        Optional<SupplierPayment> previous = payments.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            SupplierPayment p = previous.get();
            boolean same = p.getInvoiceId().equals(invoice.getId()) && p.getAmount().compareTo(request.amount()) == 0
                    && p.getPaymentDate().equals(request.paymentDate()) && p.getPaymentMethodId().equals(request.paymentMethodId());
            if (!same) {
                throw new ConflictException("This request id was already used for a different payment.");
            }
            return new PaymentResult(toResponse(p, methodName(p.getPaymentMethodId())), false);
        }
        if (invoice.isCancelled()) {
            throw new BusinessRuleException("This invoice was cancelled and can't be paid.");
        }
        if (!paymentMethods.isActive(request.paymentMethodId())) {
            throw new InvalidRequestException("Choose a payment method from the Settings list.");
        }
        BigDecimal outstanding = invoice.getTotalAmount().subtract(payments.sumActive(invoice.getId())).max(BigDecimal.ZERO);
        if (request.amount().compareTo(outstanding) > 0) {
            throw new InvalidRequestException("More than the " + Money.format(outstanding) + " still owed on this invoice.");
        }
        SupplierPayment saved = payments.saveAndFlush(new SupplierPayment(invoice.getId(), request.paymentDate(), Money.amount(request.amount()),
                request.paymentMethodId(), SupplierText.trimToNull(request.reference()), SupplierText.trimToNull(request.notes()),
                request.requestId()));
        activityService.record(invoice.getProjectId(), ActivityKind.EXPENSE, "Supplier payment: " + Money.format(saved.getAmount())
                + " on invoice " + invoice.getInvoiceNo() + ".");
        return new PaymentResult(toResponse(saved, methodName(saved.getPaymentMethodId())), true);
    }

    private Long project(SupplierInvoiceCreateRequest request) {
        if (request.allocation() == InvoiceAllocation.STOCK) {
            if (request.projectId() != null) {
                throw new InvalidRequestException("A stock invoice has no project; choose \"Project\" for goods delivered to a site.");
            }
            return null;
        }
        if (request.projectId() == null) {
            throw new InvalidRequestException("Choose the project the goods were delivered to.");
        }
        ProjectResponse project;
        try {
            project = projectService.get(request.projectId());
        } catch (NotFoundException ex) {
            throw new InvalidRequestException("The selected project does not exist.");
        }
        if (project.type() != ProjectType.CONSTRUCTION) {
            throw new InvalidRequestException("Supplier invoices can only be delivered to construction projects.");
        }
        return project.id();
    }

    private PlannedLine plan(InvoiceAllocation allocation, SupplierInvoiceCreateRequest.Line line, int lineNo) {
        String description = SupplierText.trimToNull(line.description());
        if (line.materialId() != null) {
            MaterialResponse material;
            try {
                material = materialService.get(line.materialId());
            } catch (NotFoundException ex) {
                throw new InvalidRequestException("Line " + lineNo + ": the selected material does not exist.");
            }
            if (allocation == InvoiceAllocation.STOCK) {
                if (!material.active()) {
                    throw new InvalidRequestException("Line " + lineNo + ": " + material.name() + " is inactive.");
                }
                if (material.unitId() != line.unitId()) {
                    throw new InvalidRequestException("Line " + lineNo + ": " + material.name() + " is kept in " + material.unit()
                            + "; enter the line in that unit.");
                }
            }
            description = description != null ? description : material.name();
        } else if (allocation == InvoiceAllocation.STOCK) {
            throw new InvalidRequestException("Line " + lineNo + ": choose the material for a stock invoice.");
        }
        if (description == null) {
            throw new InvalidRequestException("Line " + lineNo + ": enter a description.");
        }
        if (!units.isActive(line.unitId())) {
            throw new InvalidRequestException("Line " + lineNo + ": choose a unit from the Settings list.");
        }
        return new PlannedLine(lineNo, line.materialId(), description, line.quantity(), line.unitId(), line.unitPrice(),
                Money.amount(line.quantity().multiply(line.unitPrice())));
    }

    private List<SupplierInvoiceResponse> rows(SupplierInvoiceFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("The start date must be on or before the end date.");
        }
        List<SupplierInvoice> list = invoices.findAll(matching(filter), Sort.by(Sort.Order.desc("invoiceDate"), Sort.Order.desc("id")));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> paid = payments.sumActiveByInvoice(list.stream().map(SupplierInvoice::getId).toList()).stream()
                .collect(Collectors.toMap(SupplierPaymentRepository.InvoiceTotal::getInvoiceId, SupplierPaymentRepository.InvoiceTotal::getTotal));
        Map<Long, String> names = supplierService.findNames(list.stream().map(SupplierInvoice::getSupplierId).collect(Collectors.toSet()));
        LocalDate today = LocalDate.now(clock);
        return list.stream().map(i -> toResponse(i, names.get(i.getSupplierId()), paid.getOrDefault(i.getId(), BigDecimal.ZERO), today))
                .filter(r -> filter.status() == null || r.status() == filter.status())
                .toList();
    }

    private static Specification<SupplierInvoice> matching(SupplierInvoiceFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.supplierId() != null) {
                predicates.add(cb.equal(root.get("supplierId"), filter.supplierId()));
            }
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get("projectId"), filter.projectId()));
            }
            if (filter.allocation() != null) {
                predicates.add(cb.equal(root.get("allocation"), filter.allocation()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SupplierInvoiceDetail detail(SupplierInvoice invoice) {
        SupplierInvoiceResponse response = toResponse(invoice, supplierService.findNames(List.of(invoice.getSupplierId())).get(invoice.getSupplierId()),
                payments.sumActive(invoice.getId()), LocalDate.now(clock));
        List<SupplierInvoiceLine> rows = lines.findByInvoiceIdOrderByLineNo(invoice.getId());
        Map<Long, String> unitNames = units.findNames(rows.stream().map(SupplierInvoiceLine::getUnitId).collect(Collectors.toSet()));
        List<SupplierInvoiceDetail.Line> lineViews = rows.stream().map(l -> new SupplierInvoiceDetail.Line(l.getId(), l.getLineNo(),
                l.getMaterialId(), l.getDescription(), l.getQuantity(), l.getUnitId(), unitNames.get(l.getUnitId()), l.getUnitPrice(),
                l.getLineAmount())).toList();
        return new SupplierInvoiceDetail(response, lineViews, paymentViews(invoice.getId()));
    }

    private List<SupplierPaymentResponse> paymentViews(long invoiceId) {
        List<SupplierPayment> list = payments.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoiceId);
        Map<Long, String> names = paymentMethods.findNames(list.stream().map(SupplierPayment::getPaymentMethodId).collect(Collectors.toSet()));
        return list.stream().map(p -> toResponse(p, names.get(p.getPaymentMethodId()))).toList();
    }

    private static SupplierInvoiceResponse toResponse(SupplierInvoice i, String supplierName, BigDecimal paidAmount, LocalDate today) {
        BigDecimal paid = Money.amount(paidAmount);
        BigDecimal outstanding = Money.amount(i.getTotalAmount().subtract(paid).max(BigDecimal.ZERO));
        if (i.isCancelled()) {
            outstanding = Money.amount(BigDecimal.ZERO);
        }
        InvoiceStatus status = i.isCancelled() ? InvoiceStatus.CANCELLED
                : outstanding.signum() == 0 ? InvoiceStatus.PAID
                : i.getDueDate().isBefore(today) ? InvoiceStatus.OVERDUE
                : paid.signum() > 0 ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.UNPAID;
        return new SupplierInvoiceResponse(i.getId(), i.getSupplierId(), supplierName, i.getInvoiceNo(), i.getInvoiceDate(), i.getDueDate(),
                i.getAllocation(), i.getProjectId(), Money.amount(i.getTotalAmount()), paid, outstanding, status, i.getNotes(),
                i.getCancelledAt(), i.getCancelReason(), i.getAttachmentFilename(), i.getAttachmentSize(), i.getCreatedAt(), i.getVersion());
    }

    private static SupplierPaymentResponse toResponse(SupplierPayment p, String method) {
        return new SupplierPaymentResponse(p.getId(), p.getInvoiceId(), p.getPaymentDate(), p.getAmount(), p.getPaymentMethodId(), method,
                p.getReference(), p.getNotes(), p.getRequestId(), p.isVoided(), p.getVoidedAt(), p.getVoidReason(), p.getCreatedAt(),
                p.getVersion());
    }

    private String methodName(long id) {
        return paymentMethods.findNames(List.of(id)).get(id);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return Money.amount(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static NotFoundException notFound() {
        return new NotFoundException("Supplier invoice not found.");
    }

    private record PlannedLine(int lineNo, Long materialId, String description, BigDecimal quantity, long unitId, BigDecimal unitPrice,
            BigDecimal amount) {
    }

    // created=false when the requestId was already used
    public record PaymentResult(SupplierPaymentResponse payment, boolean created) {
    }
}
