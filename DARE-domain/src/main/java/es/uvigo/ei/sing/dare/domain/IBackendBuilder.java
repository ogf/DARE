package es.uvigo.ei.sing.dare.domain;

import java.util.Collection;
import java.util.Map;

/**
 * This interface is handy for allowing to create a {@link IBackend} from
 * Clojure.
 *
 */
public interface IBackendBuilder {

    public IBackend build(Map<String, ? extends Object> parameters);

    public Collection<String> getParametersNeeded();

}
