package ih.audit.core;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "ih.audit")
@StaticInitSafe
public interface Parameters {

    DecisionControl decisionControl();

    interface DecisionControl {
        // Base address of the Aletyx Decision Control server, without a
        // trailing slash; management and runtime API paths are appended to it.
        @WithDefault("http://localhost:8880")
        String baseUrl();
    }
}
