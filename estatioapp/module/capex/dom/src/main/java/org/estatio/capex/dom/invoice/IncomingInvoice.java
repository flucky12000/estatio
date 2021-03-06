package org.estatio.capex.dom.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.Indices;
import javax.jdo.annotations.InheritanceStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Queries;
import javax.jdo.annotations.Query;
import javax.validation.constraints.Digits;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.joda.time.LocalDate;

import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.ActionLayout;
import org.apache.isis.applib.annotation.BookmarkPolicy;
import org.apache.isis.applib.annotation.Contributed;
import org.apache.isis.applib.annotation.DomainObject;
import org.apache.isis.applib.annotation.DomainObjectLayout;
import org.apache.isis.applib.annotation.Editing;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.MinLength;
import org.apache.isis.applib.annotation.Mixin;
import org.apache.isis.applib.annotation.Programmatic;
import org.apache.isis.applib.annotation.SemanticsOf;
import org.apache.isis.applib.annotation.Where;
import org.apache.isis.applib.util.TitleBuffer;
import org.apache.isis.schema.utils.jaxbadapters.PersistentEntityAdapter;

import org.incode.module.base.dom.valuetypes.LocalDateInterval;
import org.incode.module.document.dom.impl.docs.Document;

import org.estatio.capex.dom.documents.BudgetItemChooser;
import org.estatio.capex.dom.documents.LookupAttachedPdfService;
import org.estatio.capex.dom.invoice.approval.IncomingInvoiceApprovalState;
import org.estatio.capex.dom.invoice.approval.IncomingInvoiceApprovalStateTransition;
import org.estatio.capex.dom.invoice.approval.triggers.IncomingInvoice_triggerAbstract;
import org.estatio.capex.dom.project.Project;
import org.estatio.capex.dom.state.State;
import org.estatio.capex.dom.state.StateTransition;
import org.estatio.capex.dom.state.StateTransitionType;
import org.estatio.capex.dom.state.Stateful;
import org.estatio.capex.dom.util.PeriodUtil;
import org.estatio.dom.asset.Property;
import org.estatio.dom.budgeting.budgetitem.BudgetItem;
import org.estatio.dom.charge.Charge;
import org.estatio.dom.charge.ChargeRepository;
import org.estatio.dom.financial.bankaccount.BankAccount;
import org.estatio.dom.financial.bankaccount.BankAccountRepository;
import org.estatio.dom.invoice.Invoice;
import org.estatio.dom.invoice.InvoiceItem;
import org.estatio.dom.invoice.InvoiceStatus;
import org.estatio.dom.invoice.PaymentMethod;
import org.estatio.dom.party.Party;
import org.estatio.dom.tax.Tax;

import lombok.Getter;
import lombok.Setter;

@PersistenceCapable(
        identityType = IdentityType.DATASTORE
        // unused since rolled-up to superclass:
        //,schema = "dbo"
        //,table = "IncomingInvoice"
)
@javax.jdo.annotations.Inheritance(
        strategy = InheritanceStrategy.SUPERCLASS_TABLE)
@javax.jdo.annotations.Discriminator(
        "incomingInvoice.IncomingInvoice"
)
@Queries({
        @Query(
                name = "findByApprovalState", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE approvalState == :approvalState "),
        @Query(
                name = "findByApprovalStateAndPaymentMethod", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE approvalState == :approvalState "
                        + "   && paymentMethod == :paymentMethod "),
        @Query(
                name = "findByInvoiceNumber", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE invoiceNumber == :invoiceNumber "),
        @Query(
                name = "findByInvoiceNumberAndSeller", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE invoiceNumber == :invoiceNumber "
                        + " && seller == :seller "),
        @Query(
                name = "findByInvoiceNumberAndSellerAndInvoiceDate", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE invoiceNumber == :invoiceNumber "
                        + "   && seller == :seller "
                        + "   && invoiceDate == :invoiceDate "),
        @Query(
                name = "findByInvoiceDateBetween", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE invoiceDate >= :fromDate "
                        + "   && invoiceDate <= :toDate "),
        @Query(
                name = "findByDueDateBetween", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE dueDate >= :fromDate "
                        + "   && dueDate <= :toDate "),
        @Query(
                name = "findByDateReceivedBetween", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE dateReceived >= :fromDate "
                        + "   && dateReceived <= :toDate "),
        @Query(
                name = "findNotInAnyPaymentBatchByApprovalStateAndPaymentMethod", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE !(SELECT invoice "
                        +         "  FROM org.estatio.capex.dom.payment.PaymentLine).contains(this) "
                        + "   && approvalState == :approvalState "
                        + "   && paymentMethod == :paymentMethod "
                        + "ORDER BY invoiceDate ASC " // oldest first
        ),
        @Query(
                name = "findByBankAccount", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoice "
                        + "WHERE bankAccount == :bankAccount "
                        + "ORDER BY invoiceDate DESC " // newest first
        )
})
@Indices({
        @Index(name = "IncomingInvoice_approvalState_IDX", members = { "approvalState" })
})
// unused, since rolled-up
//@Unique(name = "IncomingInvoice_invoiceNumber_UNQ", members = { "invoiceNumber" })
@DomainObject(
        editing = Editing.DISABLED,
        objectType = "incomingInvoice.IncomingInvoice",
        persistingLifecycleEvent = IncomingInvoice.ObjectPersistingEvent.class,
        persistedLifecycleEvent = IncomingInvoice.ObjectPersistedEvent.class,
        removingLifecycleEvent = IncomingInvoice.ObjectRemovingEvent.class
)
@DomainObjectLayout(
        bookmarking = BookmarkPolicy.AS_ROOT
)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
public class IncomingInvoice extends Invoice<IncomingInvoice> implements SellerBankAccountCreator, Stateful {

    public static class ObjectPersistingEvent
            extends org.apache.isis.applib.services.eventbus.ObjectPersistingEvent <IncomingInvoice> {
    }
    public static class ObjectPersistedEvent
            extends org.apache.isis.applib.services.eventbus.ObjectPersistedEvent <IncomingInvoice> {
    }
    public static class ObjectRemovingEvent
            extends org.apache.isis.applib.services.eventbus.ObjectRemovingEvent <IncomingInvoice> {
    }

    public IncomingInvoice() {
        super("seller,invoiceNumber");
    }

    public IncomingInvoice(
            final IncomingInvoiceType typeIfAny,
            final String invoiceNumber,
            final Property property,
            final String atPath,
            final Party buyer,
            final Party seller,
            final LocalDate invoiceDate,
            final LocalDate dueDate,
            final PaymentMethod paymentMethod,
            final InvoiceStatus invoiceStatus,
            final LocalDate dateReceived,
            final BankAccount bankAccount,
            final IncomingInvoiceApprovalState approvalStateIfAny){
        super("invoiceNumber");
        setType(typeIfAny);
        setInvoiceNumber(invoiceNumber);
        setProperty(property);
        setApplicationTenancyPath(atPath);
        setBuyer(buyer);
        setSeller(seller);
        setInvoiceDate(invoiceDate);
        setDueDate(dueDate);
        setPaymentMethod(paymentMethod);
        setStatus(invoiceStatus);
        setDateReceived(dateReceived);
        setBankAccount(bankAccount);
        setApprovalState(approvalStateIfAny);
    }

    public String title() {
        final TitleBuffer buf = new TitleBuffer();

        final Optional<Document> document = lookupAttachedPdfService.lookupIncomingInvoicePdfFrom(this);
        document.ifPresent(d -> buf.append(d.getName()));

        final Party seller = getSeller();
        if(seller != null) {
            buf.append(": ", seller);
        }

        final String invoiceNumber = getInvoiceNumber();
        if(invoiceNumber != null) {
            buf.append(", ", invoiceNumber);
        }

        return buf.toString();
    }

    @Mixin(method="act")
    public static class addItem {
        private final IncomingInvoice incomingInvoice;
        public addItem(final IncomingInvoice incomingInvoice) {
            this.incomingInvoice = incomingInvoice;
        }

        @MemberOrder(name="items", sequence = "1")
        public IncomingInvoice act(
                final IncomingInvoiceType type,
                final Charge charge,
                final String description,
                @Digits(integer=13, fraction = 2)
                final BigDecimal netAmount,
                @Nullable
                @Digits(integer=13, fraction = 2)
                final BigDecimal vatAmount,
                @Digits(integer=13, fraction = 2)
                final BigDecimal grossAmount,
                @Nullable final Tax tax,
                @Nullable final LocalDate dueDate,
                @Nullable final String period,
                @Nullable final Property property,
                @Nullable final Project project,
                @Nullable final BudgetItem budgetItem) {
            incomingInvoiceItemRepository.addItem(
                    incomingInvoice,
                    type,
                    charge,
                    description,
                    netAmount,
                    vatAmount,
                    grossAmount,
                    tax,
                    dueDate,
                    period,
                    property,
                    project,
                    budgetItem);
            return incomingInvoice;
        }

        public String disableAct() {
            return incomingInvoice.reasonDisabledDueToState();
        }

        public IncomingInvoiceType default0Act() {
            return incomingInvoice.getType();
        }

        public LocalDate default7Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getDueDate);
        }

        public String default8Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getStartDate)!=null ? PeriodUtil.periodFromInterval(new LocalDateInterval(incomingInvoice.ofFirstItem(IncomingInvoiceItem::getStartDate), incomingInvoice.ofFirstItem(IncomingInvoiceItem::getEndDate))) : null;
        }

        public Property default9Act() {
            return incomingInvoice.getProperty();
        }

        public Project default10Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getProject);
        }

        public List<Charge> choices1Act(){
            return chargeRepository.allIncoming();
        }

        public List<BudgetItem> choices11Act(
                final IncomingInvoiceType type,
                final Charge charge,
                final String description,
                final BigDecimal netAmount,
                final BigDecimal vatAmount,
                final BigDecimal grossAmount,
                final Tax tax,
                final LocalDate dueDate,
                final String period,
                final Property property,
                final Project project,
                final BudgetItem budgetItem) {

            return budgetItemChooser.choicesBudgetItemFor(property, charge);
        }

        public String validateAct(
                final IncomingInvoiceType type,
                final Charge charge,
                final String description,
                final BigDecimal netAmount,
                final BigDecimal vatAmount,
                final BigDecimal grossAmount,
                final Tax tax,
                final LocalDate dueDate,
                final String period,
                final Property property,
                final Project project,
                final BudgetItem budgetItem){
            if (period==null) return null; // period is optional
            return PeriodUtil.reasonInvalidPeriod(period);
        }

        @Inject
        BudgetItemChooser budgetItemChooser;

        @Inject
        IncomingInvoiceItemRepository incomingInvoiceItemRepository;
        
        @Inject
        ChargeRepository chargeRepository;

    }

    @Mixin(method="act")
    public static class splitItem {

        private final IncomingInvoice incomingInvoice;
        public splitItem(final IncomingInvoice incomingInvoice) {
            this.incomingInvoice = incomingInvoice;
        }

        @MemberOrder(name = "items", sequence = "2")
        public IncomingInvoice act(
                final IncomingInvoiceItem itemToSplit,
                final String newItemDescription,
                @Digits(integer = 13, fraction = 2)
                final BigDecimal newItemNetAmount,
                @Nullable
                @Digits(integer = 13, fraction = 2)
                final BigDecimal newItemVatAmount,
                @Nullable
                final Tax newItemtax,
                @Digits(integer = 13, fraction = 2)
                final BigDecimal newItemGrossAmount,
                final Charge newItemCharge,
                @Nullable
                final Property newItemProperty,
                @Nullable
                final Project newItemProject,
                @Nullable
                final BudgetItem newItemBudgetItem,
                final String newItemPeriod
        ) {
            itemToSplit.subtractAmounts(newItemNetAmount, newItemVatAmount, newItemGrossAmount);
            incomingInvoiceItemRepository.addItem(
                    incomingInvoice,
                    incomingInvoice.getType(),
                    newItemCharge,
                    newItemDescription,
                    newItemNetAmount,
                    newItemVatAmount,
                    newItemGrossAmount,
                    newItemtax,
                    incomingInvoice.getDueDate(),
                    newItemPeriod,
                    newItemProperty,
                    newItemProject,
                    newItemBudgetItem
                    );
            return incomingInvoice;
        }

        public String disableAct() {
            if (incomingInvoice.isImmutable()) {
                return incomingInvoice.reasonDisabledDueToState();
            }
            return incomingInvoice.getItems().isEmpty() ? "No items" : null;
        }

        public IncomingInvoiceItem default0Act() {
            return incomingInvoice.firstItemIfAny()!=null ? (IncomingInvoiceItem) incomingInvoice.getItems().first() : null;
        }

        public Tax default4Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getTax);
        }

        public Charge default6Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getCharge);
        }

        public Property default7Act() {
            return incomingInvoice.getProperty();
        }

        public Project default8Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getProject);
        }

        public BudgetItem default9Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getBudgetItem);
        }

        public String default10Act() {
            return incomingInvoice.ofFirstItem(IncomingInvoiceItem::getPeriod);
        }

        public List<IncomingInvoiceItem> choices0Act() {
            return incomingInvoice.getItems().stream().map(IncomingInvoiceItem.class::cast).collect(Collectors.toList());
        }

        public List<Charge> choices6Act(){
            return chargeRepository.allIncoming();
        }

        public List<BudgetItem> choices9Act(
                final IncomingInvoiceItem item,
                final String newItemDescription,
                final BigDecimal newItemNetAmount,
                final BigDecimal newItemVatAmount,
                final Tax newItemtax,
                final BigDecimal newItemGrossAmount,
                final Charge newItemCharge,
                final Property newItemProperty,
                final Project newItemProject,
                final BudgetItem newItemBudgetItem,
                final String newItemPeriod) {

            return budgetItemChooser.choicesBudgetItemFor(newItemProperty, newItemCharge);
        }

        public String validateAct(
                final IncomingInvoiceItem item,
                final String newItemDescription,
                final BigDecimal newItemNetAmount,
                final BigDecimal newItemVatAmount,
                final Tax newItemtax,
                final BigDecimal newItemGrossAmount,
                final Charge newItemCharge,
                final Property newItemProperty,
                final Project newItemProject,
                final BudgetItem newItemBudgetItem,
                final String newItemPeriod){
            return PeriodUtil.reasonInvalidPeriod(newItemPeriod);
        }

        @Inject
        IncomingInvoiceItemRepository incomingInvoiceItemRepository;

        @Inject
        BudgetItemChooser budgetItemChooser;

        @Inject
        ChargeRepository chargeRepository;

    }

    @Programmatic
    public <T> T ofFirstItem(final Function<IncomingInvoiceItem, T> f) {
        final Optional<IncomingInvoiceItem> firstItemIfAny = firstItemIfAny();
        return firstItemIfAny.map(f).orElse(null);
    }

    @Programmatic
    public Optional<IncomingInvoiceItem> firstItemIfAny() {
        return  getItems().stream()
                .filter(IncomingInvoiceItem.class::isInstance)
                .map(IncomingInvoiceItem.class::cast)
                .findFirst();
    }

    @Mixin(method = "act")
    public static class mergeItems {

        private final IncomingInvoice incomingInvoice;
        public mergeItems(final IncomingInvoice incomingInvoice) {
            this.incomingInvoice = incomingInvoice;
        }

        @MemberOrder(name = "items", sequence = "3")
        public IncomingInvoice act(
                final IncomingInvoiceItem item,
                final IncomingInvoiceItem mergeInto){
            incomingInvoiceItemRepository.mergeItems(item, mergeInto);
            return incomingInvoice;
        }

        public String disableAct() {
            if (incomingInvoice.isImmutable()) {
                return incomingInvoice.reasonDisabledDueToState();
            }
            return incomingInvoice.getItems().size() < 2 ? "Merge needs 2 or more items" : null;
        }

        public IncomingInvoiceItem default0Act() {
            return incomingInvoice.firstItemIfAny()!=null ? (IncomingInvoiceItem) incomingInvoice.getItems().last() : null;
        }

        public IncomingInvoiceItem default1Act() {
            return incomingInvoice.firstItemIfAny()!=null ? (IncomingInvoiceItem) incomingInvoice.getItems().first() : null;
        }

        public List<IncomingInvoiceItem> choices0Act() {
            return incomingInvoice.getItems().stream().map(IncomingInvoiceItem.class::cast).collect(Collectors.toList());
        }

        public List<IncomingInvoiceItem> choices1Act(
                final IncomingInvoiceItem item,
                final IncomingInvoiceItem mergeInto) {
            return incomingInvoice.getItems().stream().filter(x->!x.equals(item)).map(IncomingInvoiceItem.class::cast).collect(Collectors.toList());
        }

        @Inject
        IncomingInvoiceItemRepository incomingInvoiceItemRepository;

    }


    @Mixin(method="act")
    public static class changeBankAccount extends IncomingInvoice_triggerAbstract {

        private final IncomingInvoice incomingInvoice;

        public changeBankAccount(final IncomingInvoice incomingInvoice) {
            super(incomingInvoice, Arrays.asList(IncomingInvoiceApprovalState.NEW), null);
            this.incomingInvoice = incomingInvoice;
        }

        @Action(semantics = SemanticsOf.IDEMPOTENT)
        @ActionLayout(contributed= Contributed.AS_ACTION)
        public IncomingInvoice act(
                final BankAccount bankAccount,
                @Nullable final String comment){
            incomingInvoice.setBankAccount(bankAccount);
            trigger(comment, null);
            return  incomingInvoice;
        }

        public boolean hideAct() {
            return cannotTransition();
        }

        public List<BankAccount> autoComplete0Act(@MinLength(3) final String searchString){
            if (incomingInvoice.getSeller()!=null) {
                return bankAccountRepository.findBankAccountsByOwner(incomingInvoice.getSeller());
            } else {
                // empty
                return new ArrayList<>();
            }
        }

        @Inject BankAccountRepository bankAccountRepository;

    }

    /**
     * Default type, used for routing.
     *
     * <p>
     *     This can be overridden for each invoice item.
     * </p>
     */
    @Getter @Setter
    @Column(allowsNull = "false")
    private IncomingInvoiceType type;

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    public IncomingInvoice editType(
            final IncomingInvoiceType type,
            final boolean changeOnItemsAsWell){
        if (changeOnItemsAsWell){
            getItems().stream().map(IncomingInvoiceItem.class::cast).forEach(x->x.setIncomingInvoiceType(type));
        }
        setType(type);
        return this;
    }

    public IncomingInvoiceType default0EditType(){
        return getType();
    }

    public boolean default1EditType(){
        return true;
    }

    /**
     * This relates to the owning property, while the child items may either also relate to the property,
     * or could potentially relate to individual units within the property.
     *
     * <p>
     *     Note that InvoiceForLease also has a reference to FixedAsset.  It's not possible to move this
     *     up to the Invoice superclass because invoicing module does not "know" about fixed assets.
     * </p>
     */
    @javax.jdo.annotations.Column(name = "propertyId", allowsNull = "true")
    @org.apache.isis.applib.annotation.Property(hidden = Where.REFERENCES_PARENT)
    @Getter @Setter
    private Property property;

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    public IncomingInvoice editProperty(
            @Nullable
            final Property property,
            final boolean changeOnItemsAsWell){
        setProperty(property);
        if (changeOnItemsAsWell){
            getItems().stream().map(IncomingInvoiceItem.class::cast).forEach(x->x.setFixedAsset(property));
        }
        return this;
    }

    public Property default0EditProperty(){
        return getProperty();
    }

    public boolean default1EditProperty(){
        return true;
    }

    @Getter @Setter
    @Column(allowsNull = "true", name = "bankAccountId")
    private BankAccount bankAccount;

    @Getter @Setter
    @Column(allowsNull = "true")
    private LocalDate dateReceived;

    @Getter @Setter
    @Column(allowsNull = "true", name="invoiceId")
    private IncomingInvoice relatesTo;

    // TODO: need to remove this from superclass, ie push down to InvoiceForLease subclass so not in this subtype
    @org.apache.isis.applib.annotation.Property(hidden = Where.EVERYWHERE)
    @Override
    public InvoiceStatus getStatus() {
        return super.getStatus();
    }


    @org.apache.isis.applib.annotation.Property(hidden = Where.ALL_TABLES)
    @javax.jdo.annotations.Column(scale = 2, allowsNull = "true")
    @Getter @Setter
    private BigDecimal netAmount;

    @org.apache.isis.applib.annotation.Property(hidden = Where.ALL_TABLES)
    @Digits(integer = 9, fraction = 2)
    public BigDecimal getVatAmount() {
        return getGrossAmount() != null && getNetAmount() != null
                ? getGrossAmount().subtract(getNetAmount())
                : null;
    }

    @javax.jdo.annotations.Column(scale = 2, allowsNull = "true")
    @Getter @Setter
    private BigDecimal grossAmount;

    @Programmatic
    public void recalculateAmounts(){
        BigDecimal netAmountTotal = BigDecimal.ZERO;
        BigDecimal grossAmountTotal = BigDecimal.ZERO;
        for (InvoiceItem item : getItems()){
            if (item.getNetAmount()!=null) {
                netAmountTotal = netAmountTotal.add(item.getNetAmount());
            }
            if (item.getGrossAmount()!=null) {
                grossAmountTotal = grossAmountTotal.add(item.getGrossAmount());
            }
        }
        setNetAmount(netAmountTotal);
        setGrossAmount(grossAmountTotal);
    }

    @Programmatic
    @Override
    public boolean isImmutable() {
        return reasonDisabledDueToState()!=null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    public IncomingInvoice editInvoiceNumber(
            @Nullable
            final String invoiceNumber){
        setInvoiceNumber(invoiceNumber);
        return this;
    }

    public String default0EditInvoiceNumber(){
        return getInvoiceNumber();
    }

    public String disableEditInvoiceNumber(){
        return reasonDisabledDueToState();
    }


    @Action(semantics = SemanticsOf.IDEMPOTENT)
    public IncomingInvoice editBuyer(
            @Nullable
            final Party buyer){
        setBuyer(buyer);
        return this;
    }

    public Party default0EditBuyer(){
        return getBuyer();
    }

    public String disableEditBuyer(){
        return reasonDisabledDueToState();
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    public IncomingInvoice editSeller(
            @Nullable
            final Party seller){
        setSeller(seller);
        return this;
    }

    public Party default0EditSeller(){
        return getSeller();
    }

    public String disableEditSeller(){
        if (isImmutable()){
            return reasonDisabledDueToState();
        }
        return sellerIsImmutable() ? "Seller is immutable because an item is linked to an order" : null;
    }

    private boolean sellerIsImmutable(){
        for (InvoiceItem item : getItems()){
            IncomingInvoiceItem ii = (IncomingInvoiceItem) item;
            if (ii.isLinkedToOrderItem()){
                return true;
            }
        }
        return false;
    }

    public IncomingInvoice changeDates(
            @Nullable
            final LocalDate dateReceived,
            @Nullable
            final LocalDate invoiceDate,
            @Nullable
            final LocalDate dueDate
    ){
        setDateReceived(dateReceived);
        setInvoiceDate(invoiceDate);
        setDueDate(dueDate);
        return this;
    }

    public LocalDate default0ChangeDates(){
        return getDateReceived();
    }

    public LocalDate default1ChangeDates(){
        return getInvoiceDate();
    }

    public LocalDate default2ChangeDates(){
        return getDueDate();
    }

    public String disableChangeDates(){
        return reasonDisabledDueToState();
    }

    @Getter @Setter
    @javax.jdo.annotations.Column(allowsNull = "false")
    private IncomingInvoiceApprovalState approvalState;

    @Override
    public <
            DO,
            ST extends StateTransition<DO, ST, STT, S>,
            STT extends StateTransitionType<DO, ST, STT, S>,
            S extends State<S>
    > S getStateOf(
            final Class<ST> stateTransitionClass) {
        if(stateTransitionClass == IncomingInvoiceApprovalStateTransition.class) {
            return (S) approvalState;
        }
        return null;
    }

    @Override
    public <
            DO,
            ST extends StateTransition<DO, ST, STT, S>,
            STT extends StateTransitionType<DO, ST, STT, S>,
            S extends State<S>
    > void setStateOf(
            final Class<ST> stateTransitionClass, final S newState) {
        if(stateTransitionClass == IncomingInvoiceApprovalStateTransition.class) {
            setApprovalState( (IncomingInvoiceApprovalState) newState );
        }
    }


    @Programmatic
    public String reasonDisabledDueToState() {
        final IncomingInvoiceApprovalState approvalState1 = getApprovalState();
        // guard for historic invoices (and invoice items)
        if (approvalState1==null){
            return "Cannot modify";
        }
        switch (approvalState1) {
        case NEW:
        case COMPLETED:
            return null;
        default:
            return "Cannot modify because invoice is in state of " + getApprovalState();
        }
    }

    @Programmatic
    public String reasonIncomplete(){
        StringBuffer buffer = new StringBuffer();
        if (getInvoiceNumber()==null){
            buffer.append("invoice number, ");
        }
        if (getBuyer()==null){
            buffer.append("buyer, ");
        }
        if (getSeller()==null){
            buffer.append("seller, ");
        }
        if (getDateReceived()==null){
            buffer.append("date received, ");
        }
        if (getDueDate()==null){
            buffer.append("due date, ");
        }
        if (getPaymentMethod()==null){
            buffer.append("payment method, ");
        }
        if (getNetAmount()==null){
            buffer.append("net amount, ");
        }
        if (getGrossAmount()==null){
            buffer.append("gross amount, ");
        }
        if (getBankAccount() == null) {
            buffer.append("bank account, ");
        }

        if (reasonItemsIncomplete()!=null){
            buffer.append(reasonItemsIncomplete());
        }

        if (buffer.length()==0){
            return null;
        } else {
            return buffer.replace(buffer.length()-2, buffer.length(), " required").toString();
        }
    }

    @Programmatic
    public String reasonItemsIncomplete(){
        StringBuffer buffer = new StringBuffer();
        for (InvoiceItem item : getItems()){
            IncomingInvoiceItem incomingInvoiceItem = (IncomingInvoiceItem) item;
            if (incomingInvoiceItem.reasonIncomplete()!=null) {
                buffer.append("(on item ");
                buffer.append(incomingInvoiceItem.getSequence().toString());
                buffer.append(") ");
                buffer.append(incomingInvoiceItem.reasonIncomplete());
            }
        }
        return buffer.length() == 0 ? null : buffer.toString();
    }

    @Inject
    LookupAttachedPdfService lookupAttachedPdfService;

}
