package es.uvigo.ei.sing.dare.domain;

/**
 * This interface is handy for allowing to create a {@link IBackend} from
 * Clojure.
 *
 */
public interface IBackendBuilder {

    public IBackend build();

}
