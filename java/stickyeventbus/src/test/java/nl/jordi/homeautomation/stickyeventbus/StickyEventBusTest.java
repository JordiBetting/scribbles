package nl.jordi.homeautomation.stickyeventbus;

import java.util.Map;

import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;

import junit.framework.Assert;
import junit.framework.TestCase;
import nl.jordi.homeautomation.stickyeventbus.StickyEventBus.ProvideCurrentStateEvent;


public class StickyEventBusTest extends TestCase {

    private StickyEventBus bus;
    private Provider provider;
    private Subscriber subscriber;

    private static class Provider {

        @ProvideCurrentStateEvent
        public String getCurrentState() {
            return "hoi";
        }

    }

    private static class Subscriber {

        public int calls = 0;

        @Subscribe
        public void assertion(final String test) {
            calls++;
        }

    }

    @Override
    protected void setUp() throws Exception {
        bus = new StickyEventBus();
        provider = new Provider();
        subscriber = new Subscriber();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        bus = null;
        subscriber = null;
        provider = null;
        super.tearDown();
    }

    public void testRegisterProviderBeforeSubscriber() {
        bus.register(provider);
        bus.register(subscriber);

        Assert.assertEquals(1, subscriber.calls);
    }

    public void testRegisterSubscriberBeforeProvider() {
        bus.register(subscriber);
        bus.register(provider);

        Assert.assertEquals(1, subscriber.calls);
    }

    public void testUnregister() {
        bus.register(provider);
        bus.register(subscriber);

        Assert.assertEquals(1, subscriber.calls);

        bus.post("black pete");

        Assert.assertEquals(2, subscriber.calls);

        bus.unregister(subscriber);
        bus.post("pepernoten");

        Assert.assertEquals(2, subscriber.calls);
    }

    public void testRegisterUnregisterRegisterProvider() {
        bus.register(provider);
        bus.unregister(provider);
        bus.register(provider);
    }

    public void testHashmapAssumptions() {
        String first = "firework";
        String second = "oliebollen";
        String sameAsFirst = "firework";

        Map<String, Integer> testMap = Maps.newHashMap();
        testMap.put(first, 1);
        testMap.put(second, 2);
        int value = testMap.put(sameAsFirst, 3);

        assertEquals(1, value);
        assertEquals(Integer.valueOf(3), testMap.get(sameAsFirst));
    }
}
