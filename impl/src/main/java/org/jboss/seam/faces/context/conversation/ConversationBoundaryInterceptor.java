/**
 * 
 */
package org.jboss.seam.faces.context.conversation;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import javax.enterprise.context.Conversation;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.jboss.seam.faces.util.Annotations;
import org.slf4j.Logger;

/**
 * Intercepts methods annotated as Conversational entry points: @{@link Begin}
 * and @{@link End}
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
@ConversationBoundary
@Interceptor
public class ConversationBoundaryInterceptor implements Serializable
{
   private static final long serialVersionUID = -2729227895205287477L;

   @Inject
   Logger log;

   @Inject
   Conversation conversation;

   @AroundInvoke
   public Object around(final InvocationContext ctx) throws Exception
   {
      Object result = null;

      try
      {
         if (Annotations.has(ctx.getMethod(), Begin.class))
         {
            beginConversation(ctx);
         }

         result = ctx.proceed();

         if (Annotations.has(ctx.getMethod(), End.class))
         {
            endConversation(ctx);
         }
      }
      catch (Exception e)
      {
         handleExceptionBegin(ctx, e);
         handleExceptionEnd(ctx, e);
         throw e;
      }

      return result;
   }

   private void handleExceptionBegin(final InvocationContext ctx, final Exception e)
   {
      if (Annotations.has(ctx.getMethod(), Begin.class))
      {
         List<? extends Class<? extends Exception>> typesPermittedByBegin = getPermittedExceptionTypesBegin(ctx.getMethod());
         for (Class<? extends Exception> type : typesPermittedByBegin)
         {
            if (type.isInstance(e) == false)
            {
               log.debug("Aborting conversation: (#0) for method: (#1.#2(...)) - Encountered Exception of type (#4), which is not in the list of exceptions permitted by @Begin.", new Object[] { conversation.getId(), ctx.getMethod().getDeclaringClass().getName(), ctx.getMethod().getName(), e.getClass().getName() });
               conversation.end();
            }
         }
      }

   }

   private void handleExceptionEnd(final InvocationContext ctx, final Exception e)
   {
      if (Annotations.has(ctx.getMethod(), End.class))
      {
         List<? extends Class<? extends Exception>> typesPermittedByEnd = getPermittedExceptionTypesEnd(ctx.getMethod());
         boolean permitted = false;
         for (Class<? extends Exception> type : typesPermittedByEnd)
         {
            if (type.isInstance(e))
            {
               permitted = true;
               conversation.end();
            }
         }
         if (!permitted)
         {
            log.debug("Conversation will remain open: (#0) for method: (#1.#2(...)) - Encountered Exception of type (#4), which is not in the list of exceptions permitted by @End.", new Object[] { conversation.getId(), ctx.getMethod().getDeclaringClass().getName(), ctx.getMethod().getName(), e.getClass().getName() });
         }
      }
   }

   private void beginConversation(final InvocationContext ctx) throws Exception
   {
      String cid = Annotations.get(ctx.getMethod(), Begin.class).id();
      if ((cid != null) && !"".equals(cid))
      {
         conversation.begin(cid);
      }
      else
      {
         conversation.begin();
      }

      long timeout = Annotations.get(ctx.getMethod(), Begin.class).timeout();
      if (timeout != -1)
      {
         conversation.setTimeout(timeout);
      }

      log.debug("Began conversation: (#0) before method: (#1.#2(...))", new Object[] { conversation.getId(), ctx.getMethod().getDeclaringClass().getName(), ctx.getMethod().getName() });
   }

   private void endConversation(final InvocationContext ctx)
   {
      log.debug("Ending conversation: (#0) after method: (#1.#2(...))", new Object[] { conversation.getId(), ctx.getMethod().getDeclaringClass().getName(), ctx.getMethod().getName() });
      conversation.end();
   }

   private List<? extends Class<? extends Exception>> getPermittedExceptionTypesBegin(final Method m)
   {
      return Arrays.asList(Annotations.get(m, Begin.class).permit());
   }

   private List<? extends Class<? extends Exception>> getPermittedExceptionTypesEnd(final Method m)
   {
      return Arrays.asList(Annotations.get(m, End.class).permit());
   }
}
