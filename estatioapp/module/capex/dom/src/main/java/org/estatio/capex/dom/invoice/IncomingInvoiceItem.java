package org.estatio.capex.dom.invoice;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.InheritanceStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Queries;
import javax.jdo.annotations.Query;
import javax.validation.constraints.Digits;

import org.joda.time.LocalDate;

import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.ActionLayout;
import org.apache.isis.applib.annotation.BookmarkPolicy;
import org.apache.isis.applib.annotation.DomainObject;
import org.apache.isis.applib.annotation.DomainObjectLayout;
import org.apache.isis.applib.annotation.Editing;
import org.apache.isis.applib.annotation.MemberOrder;
import org.apache.isis.applib.annotation.MinLength;
import org.apache.isis.applib.annotation.ParameterLayout;
import org.apache.isis.applib.annotation.Programmatic;
import org.apache.isis.applib.annotation.PromptStyle;
import org.apache.isis.applib.annotation.Property;
import org.apache.isis.applib.annotation.SemanticsOf;
import org.apache.isis.applib.annotation.Where;
import org.apache.isis.applib.services.repository.RepositoryService;

import org.incode.module.base.dom.valuetypes.LocalDateInterval;

import org.estatio.capex.dom.documents.BudgetItemChooser;
import org.estatio.capex.dom.items.FinancialItem;
import org.estatio.capex.dom.items.FinancialItemType;
import org.estatio.capex.dom.order.OrderItem;
import org.estatio.capex.dom.order.OrderItemService;
import org.estatio.capex.dom.orderinvoice.OrderItemInvoiceItemLink;
import org.estatio.capex.dom.orderinvoice.OrderItemInvoiceItemLinkRepository;
import org.estatio.capex.dom.project.Project;
import org.estatio.capex.dom.project.ProjectRepository;
import org.estatio.capex.dom.util.PeriodUtil;
import org.estatio.dom.asset.FixedAsset;
import org.estatio.dom.budgeting.budgetitem.BudgetItem;
import org.estatio.dom.charge.Applicability;
import org.estatio.dom.charge.Charge;
import org.estatio.dom.charge.ChargeRepository;
import org.estatio.dom.invoice.Invoice;
import org.estatio.dom.invoice.InvoiceItem;
import org.estatio.dom.tax.Tax;

import lombok.Getter;
import lombok.Setter;

@PersistenceCapable(
        identityType = IdentityType.DATASTORE

        // unused since rolled-up to superclass:
        //,schema = "dbo"
        //,table = "IncomingInvoiceItem"
)
@javax.jdo.annotations.Inheritance(
        strategy = InheritanceStrategy.SUPERCLASS_TABLE)
@Queries({
        @Query(
                name = "findByInvoiceAndChargeAndSequence", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoiceItem "
                        + "WHERE invoice == :invoice "
                        + "   && charge == :charge "
                        + "   && sequence == :sequence "),
        @Query(
                name = "findByProjectAndCharge", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoiceItem "
                        + "WHERE project == :project "
                        + "   && charge == :charge "),
        @Query(
                name = "findByProject", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoiceItem "
                        + "WHERE project == :project "),
        @Query(
                name = "findByBudgetItem", language = "JDOQL",
                value = "SELECT "
                        + "FROM org.estatio.capex.dom.invoice.IncomingInvoiceItem "
                        + "WHERE budgetItem == :budgetItem ")
})
@DomainObject(
        editing = Editing.DISABLED,
        objectType = "incomingInvoice.IncomingInvoiceItem"
)
@javax.jdo.annotations.Discriminator(
        "incomingInvoice.IncomingInvoiceItem"
)
@DomainObjectLayout(
        bookmarking = BookmarkPolicy.AS_ROOT
)
public class IncomingInvoiceItem extends InvoiceItem<IncomingInvoiceItem> implements FinancialItem {

    public IncomingInvoiceItem(){}

    public IncomingInvoiceItem(
            final BigInteger sequence,
            final IncomingInvoice invoice,
            final IncomingInvoiceType incomingInvoiceType,
            final Charge charge,
            final String description,
            final BigDecimal netAmount,
            final BigDecimal vatAmount,
            final BigDecimal grossAmount,
            final Tax tax,
            final LocalDate dueDate,
            final LocalDate startDate,
            final LocalDate endDate,
            final org.estatio.dom.asset.Property property,
            final Project project,
            final BudgetItem budgetItem){
        super(invoice);
        this.incomingInvoiceType = incomingInvoiceType;

        setSequence(sequence);

        setCharge(charge);
        setDescription(description);

        setNetAmount(netAmount);
        setVatAmount(vatAmount);
        setGrossAmount(grossAmount);

        setDueDate(dueDate);
        setStartDate(startDate);
        setEndDate(endDate);

        setTax(tax);
        setFixedAsset(property);
        setProject(project);
        setBudgetItem(budgetItem);

    }

    @Override
    @Programmatic
    public BigDecimal value() {
        return getNetAmount();
    }

    @Override
    public FinancialItemType getType() {
        return FinancialItemType.INVOICED;
    }

    /**
     * Typically the same as the {@link IncomingInvoice#getType() type} defined by the {@link #getInvoice() parent}
     * {@link IncomingInvoice invoice}, but can be overridden if necessary.
     */
    @Getter @Setter
    @Column(allowsNull = "false")
    private IncomingInvoiceType incomingInvoiceType;

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editIncomingInvoiceType(final IncomingInvoiceType incomingInvoiceType) {
        setIncomingInvoiceType(incomingInvoiceType);
        return this;
    }

    public IncomingInvoiceType default0EditIncomingInvoiceType(){
        return getIncomingInvoiceType();
    }

    @javax.jdo.annotations.Column(name = "fixedAssetId", allowsNull = "true")
    @Property(hidden = Where.PARENTED_TABLES)
    @Getter @Setter
    private FixedAsset fixedAsset;

    @Getter @Setter
    @Column(allowsNull = "true", name="projectId")
    @Property(hidden = Where.REFERENCES_PARENT)
    private Project project;

    @Getter @Setter
    @Column(allowsNull = "true", name="budgetItemId")
    @Property(hidden = Where.REFERENCES_PARENT)
    private BudgetItem budgetItem;

    @Programmatic
    public String getPeriod(){
        return PeriodUtil.periodFromInterval(getInterval());
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    public IncomingInvoiceItem updateAmounts(
            @Digits(integer=13, fraction = 2)
            final BigDecimal netAmount,
            @Nullable
            @Digits(integer=13, fraction = 2)
            final BigDecimal vatAmount,
            @Digits(integer=13, fraction = 2)
            final BigDecimal grossAmount,
            @Nullable
            final Tax tax){
        setNetAmount(netAmount);
        setVatAmount(vatAmount);
        setGrossAmount(grossAmount);
        setTax(tax);
        IncomingInvoice invoice = (IncomingInvoice) getInvoice();
        invoice.recalculateAmounts();
        return this;
    }

    public BigDecimal default0UpdateAmounts(){
        return getNetAmount();
    }

    public BigDecimal default1UpdateAmounts(){
        return getVatAmount();
    }

    public BigDecimal default2UpdateAmounts(){
        return getGrossAmount();
    }

    public Tax default3UpdateAmounts(){
        return getTax();
    }

    public String disableUpdateAmounts(){
        return isImmutable() ? itemImmutableReason() : null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editDescription(
                                    @ParameterLayout(multiLine = DescriptionType.Meta.MULTI_LINE)
                                    final String description) {
        setDescription(description);
        return this;
    }

    public String default0EditDescription(){
        return getDescription();
    }

    public String disableEditDescription(){
        return isImmutable() ? itemImmutableReason() : null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editDueDate(final LocalDate dueDate){
        setDueDate(dueDate);
        return this;
    }

    public LocalDate default0EditDueDate(){
        return getDueDate();
    }

    public String disableEditDueDate(){
        return isImmutable() ? itemImmutableReason() : null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editCharge(@Nullable final Charge charge) {
        setCharge(charge);
        return this;
    }

    public Charge default0EditCharge(){
        return getCharge();
    }

    public List<Charge> autoComplete0EditCharge(@MinLength(3) String search){
        return chargeRepository.findByApplicabilityAndMatchOnReferenceOrName(search, Applicability.INCOMING);
    }

    public String disableEditCharge(){
        return chargeIsImmutable() ? "Charge is immutable because this item is linked to an order or budget" : null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editFixedAsset(
            @Nullable
            final org.estatio.dom.asset.Property property){
        setFixedAsset(property);
        return this;
    }

    public org.estatio.dom.asset.Property default0EditFixedAsset(){
        return (org.estatio.dom.asset.Property) getFixedAsset();
    }

    public String disableEditFixedAsset(){
        return fixedAssetIsImmutable() ? "Fixed asset is immutable because this item is linked to an order, budget or project" : null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editProject(
            @Nullable
            final Project project){
        setProject(project);
        return this;
    }

    public Project default0EditProject(){
        return getProject();
    }

    public List<Project> choices0EditProject(){
        return getFixedAsset()!=null ? projectRepository.findByFixedAsset(getFixedAsset()) : null;
    }

    public String disableEditProject(){
        return projectIsImmutable() ? "Project is immutable because this item is linked to an order" : null;
    }

    @Action(semantics = SemanticsOf.IDEMPOTENT)
    @ActionLayout(promptStyle = PromptStyle.INLINE_AS_IF_EDIT)
    public IncomingInvoiceItem editBudgetItem(
            @Nullable
            final BudgetItem budgetItem){
        setBudgetItem(budgetItem);
        if (budgetItem!=null) setCharge(budgetItem.getCharge());
        if (budgetItem!=null) setFixedAsset(budgetItem.getBudget().getProperty());
        return this;
    }

    public BudgetItem default0EditBudgetItem(){
        return getBudgetItem();
    }

    public List<BudgetItem> choices0EditBudgetItem() {
        return budgetItemChooser.choicesBudgetItemFor((org.estatio.dom.asset.Property) getFixedAsset(), getCharge());
    }

    public String disableEditBudgetItem(){
        return budgetItemIsImmutable() ? "Budget item is immutable because this item is linked to an order or the invoice has not type service charges" : null;
    }

    public IncomingInvoiceItem editPeriod(@Nullable final String period){
        if (PeriodUtil.isValidPeriod(period)){
            setStartDate(PeriodUtil.yearFromPeriod(period).startDate());
            setEndDate(PeriodUtil.yearFromPeriod(period).endDate());
        }
        return this;
    }

    public String default0EditPeriod(){
        return PeriodUtil.periodFromInterval(new LocalDateInterval(getStartDate(), getEndDate()));
    }

    public String validateEditPeriod(final String period){
        return PeriodUtil.isValidPeriod(period) ? null : "Not a valid period";
    }

    private boolean isImmutable(){
        IncomingInvoice invoice = (IncomingInvoice) getInvoice();
        return invoice.isImmutable();
    }

    private String itemImmutableReason(){
        return "The invoice cannot be changed";
    }

    private boolean chargeIsImmutable(){
        if (this.isLinkedToOrderItem()){
            return true;
        }
        if (this.getBudgetItem()!=null){
            return true;
        }
        return false;
    }

    private boolean fixedAssetIsImmutable(){
        if (this.isLinkedToOrderItem()){
            return true;
        }
        if (this.getBudgetItem()!=null){
            return true;
        }
        if (this.getProject()!=null){
            return true;
        }
        return false;
    }

    private boolean budgetItemIsImmutable(){
        IncomingInvoice invoice = (IncomingInvoice) this.getInvoice();
        if (invoice.getType()!=IncomingInvoiceType.SERVICE_CHARGES){
            return true;
        }
        if (this.isLinkedToOrderItem()){
            return true;
        }
        return false;
    }

    private boolean projectIsImmutable(){
        if (this.isLinkedToOrderItem()){
            return true;
        }
        return false;
    }

    boolean isLinkedToOrderItem(){
        if (orderItemInvoiceItemLinkRepository.findByInvoiceItem(this).size()>0){
            return true;
        }
        return false;
    }

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Invoice removeItem(){
        IncomingInvoice invoice = (IncomingInvoice) getInvoice();
        if (isLinkedToOrderItem()){
            for (OrderItemInvoiceItemLink link : orderItemInvoiceItemLinkRepository.findByInvoiceItem(this)){
                repositoryService.removeAndFlush(link);
            }
        }
        repositoryService.removeAndFlush(this);
        invoice.recalculateAmounts();
        return invoice;
    }

    public String disableRemoveItem(){
        return isImmutable() ? itemImmutableReason() : null;
    }

    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    @MemberOrder(name = "orderItems", sequence = "1")
    public IncomingInvoiceItem updateOrCreateOrderItem(
            @Nullable
            final OrderItem orderItem){
        for (OrderItemInvoiceItemLink link : orderItemInvoiceItemLinkRepository.findByInvoiceItem(this)){
            link.remove();
        }
        if (orderItem!=null){
            orderItemInvoiceItemLinkRepository.findOrCreateLink(orderItem, this);
        }
        return this;
    }

    public OrderItem default0UpdateOrCreateOrderItem(){
        return orderItemInvoiceItemLinkRepository.findByInvoiceItem(this).size() > 0 ?
                orderItemInvoiceItemLinkRepository.findByInvoiceItem(this).get(0).getOrderItem()
                : null;
    }

    public List<OrderItem> autoComplete0UpdateOrCreateOrderItem(@MinLength(3) final String searchString){

       return orderItemService.searchOrderItem(
               searchString,
               getInvoice().getSeller(),
               getCharge(),
               getProject(),
               (org.estatio.dom.asset.Property) getFixedAsset());

    }

    public String disableUpdateOrCreateOrderItem(){
        // safeguard: there should at most be 1 linked order item
        if (orderItemInvoiceItemLinkRepository.findByInvoiceItem(this).size()>1){
            return "Error: More than 1 linked order item found";
        }
        return null; // EST-1507: invoices item can be attached to order items any time
    }

    @Programmatic
    public String reasonIncomplete(){
        StringBuffer buffer = new StringBuffer();
        if (getStartDate()==null){
            buffer.append("start date, ");
        }
        if (getEndDate()==null){
            buffer.append("end date, ");
        }
        if (getNetAmount()==null){
            buffer.append("net amount, ");
        }
        if (getGrossAmount()==null){
            buffer.append("gross amount, ");
        }

        return buffer.length() == 0 ? null : buffer.toString();
    }

    @Programmatic
    public void subtractAmounts(final BigDecimal netAmountToSubtract, final BigDecimal vatAmountToSubtract, final BigDecimal grossAmountToSubtract) {
        if (getNetAmount() != null && netAmountToSubtract != null) {
            setNetAmount(getNetAmount().subtract(netAmountToSubtract));
        }
        if (getVatAmount() != null && vatAmountToSubtract != null) {
            setVatAmount(getVatAmount().subtract(vatAmountToSubtract));
        }
        if (getGrossAmount() != null && grossAmountToSubtract != null) {
            setGrossAmount(getGrossAmount().subtract(grossAmountToSubtract));
        }
    }

    @Programmatic
    public void addAmounts(final BigDecimal netAmountToAdd, final BigDecimal vatAmountToAdd, final BigDecimal grossAmountToAdd) {
        if (getNetAmount() != null && netAmountToAdd != null) {
            setNetAmount(getNetAmount().add(netAmountToAdd));
        }
        if (getVatAmount() != null && vatAmountToAdd != null) {
            setVatAmount(getVatAmount().add(vatAmountToAdd));
        }
        if (getGrossAmount() != null && grossAmountToAdd != null) {
            setGrossAmount(getGrossAmount().add(grossAmountToAdd));
        }
    }

    @Inject
    OrderItemInvoiceItemLinkRepository orderItemInvoiceItemLinkRepository;

    @Inject
    private ChargeRepository chargeRepository;

    @Inject
    RepositoryService repositoryService;

    @Inject
    private ProjectRepository projectRepository;

    @Inject
    private OrderItemService orderItemService;

    @Inject
    private BudgetItemChooser budgetItemChooser;

}
