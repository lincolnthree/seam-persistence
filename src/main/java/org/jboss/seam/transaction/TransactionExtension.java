package org.jboss.seam.transaction;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessBean;
import javax.naming.NamingException;

import org.jboss.weld.extensions.annotated.AnnotatedTypeBuilder;
import org.jboss.weld.extensions.bean.BeanBuilder;
import org.jboss.weld.extensions.bean.BeanImpl;
import org.jboss.weld.extensions.bean.BeanLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension than provides a {@link UserTransaction} if no other UserTransaction
 * has been registered.
 * 
 * This allows the user to register a transaction via seam XML and have it
 * automatically replace the default UserTransaction implementation
 * 
 * This is not done with alternatives, because that would require specifying the
 * transaction manager on a per module basis, and some of the UserTransaction
 * implementations need to be configured via seam xml anyway, so they would have
 * to be configured twice
 * 
 * @author Stuart Douglas
 * 
 */
public class TransactionExtension implements Extension
{
   private boolean transactionRegistered = false;

   private static final Logger log = LoggerFactory.getLogger(TransactionExtension.class);

   public void processBean(@Observes ProcessBean<?> event)
   {
      Bean<?> bean = event.getBean();
      if (bean.getTypes().contains(UserTransaction.class))
      {
         if (bean.getQualifiers().isEmpty()) // not sure about this
         {
            transactionRegistered = true;
         }
         else
         {
            for (Annotation a : event.getBean().getQualifiers())
            {
               if (a.annotationType() == Default.class)
               {
                  transactionRegistered = true;
                  break;
               }
            }
         }
      }
   }

   public void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager manager)
   {
      if (!transactionRegistered)
      {
         AnnotatedTypeBuilder<UserTransaction> utbuilder = AnnotatedTypeBuilder.newInstance(UserTransaction.class);
         BeanBuilder<UserTransaction> builder = new BeanBuilder<UserTransaction>(utbuilder.create(), manager);
         builder.defineBeanFromAnnotatedType();

         Set<Bean<?>> beans = manager.getBeans(Transaction.class);
         if (beans.isEmpty())
         {
            log.error("No bean with type " + Transaction.class.getName() + " registered, SEAM TRANSACTIONS ARE DISABLED");
         }
         else if (beans.size() > 1)
         {
            log.error("More than 1 bean with type " + Transaction.class.getName() + " registered, SEAM TRANSACTIONS ARE DISABLED");
         }
         Bean<?> bean = beans.iterator().next();
         builder.setBeanLifecycle(new TransactionLifecycle(manager, (Bean) bean));
         builder.setInjectionTarget(new NoOpInjectionTarget());
         event.addBean(builder.create());
      }
   }

   private static class TransactionLifecycle implements BeanLifecycle<UserTransaction>
   {

      private final BeanManager manager;

      private final Bean<Transaction> transactionBean;

      public TransactionLifecycle(BeanManager manager, Bean<Transaction> transactionBean)
      {
         this.manager = manager;
         this.transactionBean = transactionBean;

      }

      public UserTransaction create(BeanImpl<UserTransaction> bean, CreationalContext<UserTransaction> ctx)
      {
         Transaction t = (Transaction) manager.getReference(transactionBean, Transaction.class, ctx);
         try
         {
            return t.getTransaction();
         }
         catch (NamingException e)
         {
            throw new RuntimeException(e);
         }
      }

      public void destroy(BeanImpl<UserTransaction> bean, UserTransaction arg0, CreationalContext<UserTransaction> arg1)
      {
         arg1.release();
      }

   }

   private static class NoOpInjectionTarget implements InjectionTarget<UserTransaction>
   {

      public UserTransaction produce(CreationalContext<UserTransaction> ctx)
      {
         return null;
      }

      public Set<InjectionPoint> getInjectionPoints()
      {
         return Collections.EMPTY_SET;
      }

      public void dispose(UserTransaction instance)
      {

      }

      public void preDestroy(UserTransaction instance)
      {

      }

      public void postConstruct(UserTransaction instance)
      {

      }

      public void inject(UserTransaction instance, CreationalContext<UserTransaction> ctx)
      {

      }

   }
}
