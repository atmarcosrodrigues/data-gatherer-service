package com.silibrina.tecnova.opendata.executors;

import akka.actor.ActorSystem;
import play.api.Play;
import scala.concurrent.ExecutionContext;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;

/**
 * Factory for configured executors.
 * Executors are configured in application.conf (dev) and service-broker.conf (prod).
 */
public class ExecutorFactory {

    /**
     * Gets the executor (thread pool) based on the type.
     *
     * @param type the type of the required executor.
     *
     * @return the executor for that type.
     */
    public static Executor getExecutor(ExecutorType type) {
        return new AkkaExecutor(type.context);
    }

    public enum ExecutorType {
        STORAGE("storage"),
        SEARCHER("searcher");

        private  final String context;

        ExecutorType(String context) {
            this.context = context;
        }
    }

    /**
     * Wrapper for akka executor.
     */
    private static class AkkaExecutor implements Executor {
        private final String context;
        private final ExecutionContext executionContext;

        private AkkaExecutor(@Nonnull String context) {
            this.context = context;

            executionContext = getExecutor();
        }

        @Override
        public void execute(@Nonnull Runnable command) {
            executionContext.execute(command);
        }

        private ExecutionContext getExecutor() {
            ActorSystem actorSystem = Play.current().injector().instanceOf(ActorSystem.class);
            return actorSystem.dispatchers().lookup(context);
        }
    }
}
