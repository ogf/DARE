package es.uvigo.ei.sing.dare.resources;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;

public class CacheUtil {

    public static Response cacheImmutable(Object result) {
        CacheControl cacheControl = CacheControl.valueOf("public");
        cacheControl.setMaxAge(90 * 24 * 3600); // 90 days
        return Response.ok(result).cacheControl(cacheControl).build();
    }

}
