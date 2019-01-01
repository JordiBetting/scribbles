package nl.jordi.homeautomation.stickyeventbus;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;


/**
 * EventBus with direct reply on register for sticky events.
 * 
 * Extension of Guavas {@link EventBus}. For usage, see: <a href="https://github.com/google/guava/wiki/EventBusExplained">Guava
 * Explained: EventBus</a>
 * For each event type, one provider can provide the current 'state' event using the {@link ProvideCurrentStateEvent} annotation.
 * 
 * Whenever a class registers on the {@link StickyEventBus}, all subscribe methods will be called for which a
 * {@link ProvideCurrentStateEvent} has been provided by a provider. If a data provider wants to use the {@link ProvideCurrentStateEvent}
 * annotation, it must also use the {@link EventBus#register(Object)} method. When posting events on the standard {@link EventBus},
 * registration is not needed.
 * 
 * Whenever a class containing {@link ProvideCurrentStateEvent} annotated methods registers on the {@link StickyEventBus}, a lookup will be
 * performed on which listeners have a {@link Subscribe} method for this event type. All these methods will be called using the value from
 * the {@link ProvideCurrentStateEvent} annotated method in the newly registered class.
 * 
 * Important note: Only one method of all registered classes can provide a method annotated by {@link ProvideCurrentStateEvent} for an event
 * type.
 * 
 * @author Jordi
 * @see <a href="https://code.google.com/p/guava-libraries/wiki/EventBusExplained">Guava Explained: EventBus</a>
 */
public class StickyEventBus extends EventBus {

    private static final Logger LOG = LogManager.getLogger(StickyEventBus.class);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface ProvideCurrentStateEvent {

    }

    private final Object stateMutex = new Object();
    private final Map<Class<?>, Pair<Object, Method>> currentStateMethods = Maps.newHashMap();
    private final Set<Object> registeredObjects = Sets.newHashSet();

    public StickyEventBus(final String name) {
        super(name);
    }

    public StickyEventBus() {
        super();
    }

    @Override
    public void register(final Object object) {
        if (object != null) {
        	LOG.trace("Registering {}", object);
            super.register(object);

            synchronized (stateMutex) {
                registeredObjects.add(object);

                Set<Class<?>> providedTypes = gatherDefaultProviders(object);

                automaticReceiveDefaultsToObject(object);
                automaticProvideDefaultsFromObject(object, providedTypes);
            }
        }
    }

    @Override
    public void post(final Object event) {
        LOG.trace("Post: {}", event);
        super.post(event);
    }

    private void automaticProvideDefaultsFromObject(final Object object, final Set<Class<?>> providedTypes) {
        for (Method method : object.getClass().getMethods()) {
            if (method.isAnnotationPresent(ProvideCurrentStateEvent.class) && providedTypes.contains(method.getReturnType())) {
                provideDefault(object, method);
            }
        }
    }

    private void provideDefault(final Object object, final Method method) {
        try {
            Object result = method.invoke(object, new Object[0]);
            post(result);
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
        	LOG.warn("Could not provide current state event from newly registered object to other registered objects, ignoring", e);
        }
    }

    private void automaticReceiveDefaultsToObject(final Object object) {
        Set<Class<?>> subscribeTypes = findAllSubscribeTypes(object);
        for (Class<?> clazz : subscribeTypes) {
            if (currentStateMethods.containsKey(clazz)) {
                Pair<Object, Method> pair = currentStateMethods.get(clazz);
                try {
                    Object result = pair.second.invoke(pair.first, new Object[0]);
                    post(result);
                } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                    LOG.warn("Could not provide current state event to new registered object, ignoring", e);
                }
            }
        }
    }

    private Set<Class<?>> gatherDefaultProviders(final Object object) {
        Class<?> clazz = object.getClass();
        Set<Class<?>> newMappings = Sets.newHashSet();

        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(ProvideCurrentStateEvent.class)) {
                Class<?> eventType = method.getReturnType();

                if (eventType != Void.class) {
                    Pair<Object, Method> put = currentStateMethods.put(eventType, new Pair<Object, Method>(object, method));
                    if (put != null) {
                        LOG.warn("Only one currentStateProvider for {} can be registered.", eventType.getClass().getSimpleName());
                    }
                    newMappings.add(eventType);
                    LOG.debug("Found that {} provides {}", object.getClass().getSimpleName(), eventType);
                }
            }
        }
        return newMappings;
    }

    private Set<Class<?>> findAllSubscribeTypes(final Object object) {
        return findAllTypes(object, Subscribe.class);
    }

    private Set<Class<?>> findAllTypes(final Object listener, final Class<? extends Annotation> annotation) {

        Set<Class<?>> methodsInListener = Sets.newHashSet();
        Class<?> clazz = listener.getClass();

        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                Class<?> eventType = parameterTypes[0];
                methodsInListener.add(eventType);
            }
        }
        return methodsInListener;
    }

    @Override
    public void unregister(final Object object) {
        if (object != null) {
            synchronized (stateMutex) {
                LOG.trace("Unregistering {}", object);
                registeredObjects.remove(object);

                for (Class<?> value : getProvidedDataTypesForObject(object)) {
                    currentStateMethods.remove(value);
                }
            }
            super.unregister(object);
        }
    }

    private Iterable<Class<?>> getProvidedDataTypesForObject(final Object object) {
        List<Class<?>> toRemove = Lists.newArrayList();
        for (Entry<Class<?>, Pair<Object, Method>> entry : currentStateMethods.entrySet()) {
            if ((entry != null) && (entry.getValue() != null) && object.equals(entry.getValue().first)) {
                toRemove.add(entry.getKey());
            }
        }
        return toRemove;
    }
}
