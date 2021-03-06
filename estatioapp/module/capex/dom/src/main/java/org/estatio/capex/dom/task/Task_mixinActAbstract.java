package org.estatio.capex.dom.task;

import javax.inject.Inject;

import org.assertj.core.util.Strings;

import org.estatio.dom.party.Person;
import org.estatio.dom.party.PersonRepository;

/**
 * Base class for mixins on {@link Task} that delegate to a corresponding mixin on some domain object which will
 * result in a {@link Task} being completed.
 */
public abstract class Task_mixinActAbstract<M, DO> extends Task_mixinAbstract<M,DO> {

    public Task_mixinActAbstract(final Task task, final Class<M> mixinClass) {
        super(task, mixinClass);
    }

    /**
     * Subclasses should override and make <tt>public</tt>.
     */
    protected boolean hideAct() {
        return task.isCompleted() || getDomainObjectIfAny() == null;
    }

    protected String validateCommentIfByProxy(final String comment) {
        Person meAsPerson = personRepository.me();
        if(meAsPerson != task.getPersonAssignedTo()) {
            if(Strings.isNullOrEmpty(comment)) {
                return "Comment is required for approval by proxy";
            }
        }
        return null;
    }

    @Inject
    PersonRepository personRepository;

}
