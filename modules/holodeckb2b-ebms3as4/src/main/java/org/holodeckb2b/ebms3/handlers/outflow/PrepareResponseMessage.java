/**
 * Copyright (C) 2014 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.ebms3.handlers.outflow;

import java.util.Collection;
import java.util.Collections;

import org.apache.logging.log4j.Logger;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.core.storage.StorageManager;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IPullRequest;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.storage.IErrorMessageEntity;
import org.holodeckb2b.interfaces.storage.IMessageUnitEntity;
import org.holodeckb2b.interfaces.storage.providers.StorageException;

/**
 * Is the first handler of the <i>out flow</i> which is responsible for checking that the message units generated by the
 * <i>in flow</i> handlers can be sent in one response message. The handlers may have generated multiple Error Signal 
 * message units but according to the bundling rules of the ebMS3 Core Specification only one can be included in the
 * response (see section 5.2.4 of the spec). Therefore this handler will select one Error message for inclusion in the 
 * response and set the processing state of the remaining errors to failed.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class PrepareResponseMessage extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(final IMessageProcessingContext procCtx, final Logger log) 
    																					throws StorageException {
        // Check if there are error signal messages to be included
        log.trace("Check for error signals generated during in flow to be included");
        final Collection<IErrorMessageEntity> errors = procCtx.getSendingErrors();
        if (!Utils.isNullOrEmpty(errors) && errors.size() > 1) {
            // Bundling not allowed, select one error signal to include
            log.trace("Bundling is not allowed, select one error to report");
            final IErrorMessageEntity include = selectError(errors, procCtx);
            log.debug("Include the selected error [msgID/refTo" + include.getMessageId() + "/"
            		 + include.getRefToMessageId() + "] for processing");
            procCtx.setSendingErrors(Collections.singletonList(include));
            // The other errors can not be further processed, so change their state to failed
            errors.remove(include);
            final StorageManager updateManager = HolodeckB2BCore.getStorageManager();
            for(IErrorMessageEntity e : errors) {
            	log.warn("Unable to send Error Signal [msgId=" + e.getMessageId() + "] to sender");
            	updateManager.setProcessingState(e, ProcessingState.FAILURE, "Too many errors in response");
            }
        } 

        return InvocationResponse.CONTINUE;
    }

    /**
     * Is a helper method to select the error signal that has the highest priority of sending. The priority of error
     * signals is determined by the message unit they reference and as follows:<ol>
     * <li>no referenced message unit: The error is to the complete message and can not be related to a message unit.
     * This indicates a general problem with the message and should be reported;</li>
     * <li>UserMessage</li>
     * <li>PullRequest</li>
     * <li>Error or Receipt</li>
     * </ol>
     *
     * @param errors    The collection of errors that were generated for the received message
     * @param procCtx   The message processing context
     * @return          The error signal message to include in the response
     */
    protected IErrorMessageEntity selectError(final Collection<IErrorMessageEntity> errors, 
    										final IMessageProcessingContext procCtx) {
        IErrorMessageEntity    r = null;
        int             cp = -1; // The prio of the currently selected err, 0=Error or Receipt, 1=PullReq, 2=UsrMsg

        // Now select the error with highest prio
        for(final IErrorMessageEntity e : errors) {
            final String refTo = e.getRefToMessageId();
            if (Utils.isNullOrEmpty(refTo)) {
                r = e; // This is the error signal that does not reference a message unit
                break;
            } else {
                final Class<? extends IMessageUnitEntity> type = procCtx.getReceivedMessageUnit(refTo).getClass();
                if (IUserMessage.class.isAssignableFrom(type)) {
                    r = e; cp = 2;
                } else if (IPullRequest.class.isAssignableFrom(type) && cp < 1) {
                    r = e; cp = 1;
                } else if (cp == -1) {
                    // Error references either Error or Receipt which have same prio, just select first
                    r = e; cp = 0;
                }
            }
        }
        return r;
    }
}
