package ninja.template;

import com.google.common.collect.ImmutableMap;
import com.google.inject.*;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class TemplateEngineManagerImpl implements TemplateEngineManager {

    // Keep a reference of providers rather than instances, so template engines don't have
    // to be singleton if they don't want
    private final Map<String, Provider<? extends TemplateEngine>> contentTypeToTemplateEngineMap;

	@Inject
	public TemplateEngineManagerImpl(Provider<TemplateEngineFreemarker> templateEngineFreemarker,
	                                 Provider<TemplateEngineJsonGson> templateEngineJsonGson,
                                     Injector injector) {

        Map<String, Provider<? extends TemplateEngine>> map
                = new HashMap<String, Provider<? extends TemplateEngine>>();

        // First put the built in ones in, this is so they can be overridden by
        // custom bindings
        map.put(templateEngineFreemarker.get().getContentType(), templateEngineFreemarker);
        map.put(templateEngineJsonGson.get().getContentType(), templateEngineJsonGson);

        // Now lookup all explicit bindings, and find the ones that implement TemplateEngine
        for (Map.Entry<Key<?>, Binding<?>> binding : injector.getBindings().entrySet()) {
            if (TemplateEngine.class.isAssignableFrom(binding.getKey().getTypeLiteral().getRawType())) {
                Provider<? extends TemplateEngine> provider =
                        (Provider) binding.getValue().getProvider();
                map.put(provider.get().getContentType(), provider);
            }
        }

        contentTypeToTemplateEngineMap = ImmutableMap.copyOf(map);
	}

	@Override
	public TemplateEngine getTemplateEngineForContentType(String contentType) {
        return contentTypeToTemplateEngineMap.get(contentType).get();
	}
}
