package org.estatio.capex.dom.payment.approval.triggers;

import java.util.List;

import org.estatio.capex.dom.payment.PaymentBatch;
import org.estatio.capex.dom.payment.approval.PaymentBatchApprovalState;
import org.estatio.capex.dom.payment.approval.PaymentBatchApprovalStateTransition;
import org.estatio.capex.dom.payment.approval.PaymentBatchApprovalStateTransitionType;
import org.estatio.capex.dom.triggers.DomainObject_triggerAbstract;

abstract class PaymentBatch_triggerAbstract
        extends DomainObject_triggerAbstract<
        PaymentBatch,
        PaymentBatchApprovalStateTransition,
        PaymentBatchApprovalStateTransitionType,
        PaymentBatchApprovalState> {

    PaymentBatch_triggerAbstract(
            final PaymentBatch paymentBatch,
            final List<PaymentBatchApprovalState> fromStates,
            final PaymentBatchApprovalStateTransitionType requiredTransitionTypeIfAny) {
        super(paymentBatch, PaymentBatchApprovalStateTransition.class, fromStates, requiredTransitionTypeIfAny);
    }

    PaymentBatch_triggerAbstract(
            final PaymentBatch paymentBatch,
            final PaymentBatchApprovalStateTransitionType requiredTransitionType) {
        super(paymentBatch, PaymentBatchApprovalStateTransition.class, requiredTransitionType.getFromStates(), requiredTransitionType);
    }


}
