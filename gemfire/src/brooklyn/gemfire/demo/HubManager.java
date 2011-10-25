package brooklyn.gemfire.demo;

import java.io.IOException;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.GatewayException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.util.Gateway;
import com.gemstone.gemfire.cache.util.GatewayHub;
import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;

public class HubManager implements GatewayChangeListener, RegionChangeListener {

    private Cache cache;

    public HubManager( Cache cache ) {
        this.cache = cache;
    }

    @Override
    public void gatewayAdded( String id, String endpointId, String host, int port, GatewayQueueAttributes attributes ) throws IOException {
        for(GatewayHub hub :  cache.getGatewayHubs()) {
            stopHub(hub);
            addGateway(hub,id,endpointId,host,port,attributes);
            startHub(hub);
        }
    }

    @Override
    public boolean gatewayRemoved( String id ) throws IOException {
    	boolean removed = false;
    	for( GatewayHub hub :  cache.getGatewayHubs() ) {
    		for( Gateway gateway: hub.getGateways()) {
    			if (id.equals(gateway.getId())) {
    				stopHub(hub);
    				hub.removeGateway(id);
    				startHub(hub);
    				removed = true;
    			}
    		}
    		for ( Gateway gateway: hub.getGateways() ) gateway.start();
    	}
    	return removed;
    }

    @Override
    public void regionAdded(String name) throws IOException {
    	cache.createRegionFactory(RegionShortcut.REPLICATE).setEnableGateway(new Boolean(true)).create(name);
    }

    @Override
    public boolean regionRemoved(String name) throws IOException {
    	Region<?, ?> r = cache.getRegion(name);
    	if(r != null) {
    		r.localDestroyRegion();
    		return true;
    	}
    	
    	return false;
    }

    private Gateway addGateway( GatewayHub hub,
                                String id,String endpointId, String host, int port, GatewayQueueAttributes attributes ) {
        Gateway gateway = hub.addGateway(id);
        try {
            gateway.addEndpoint(endpointId,host,port);
            gateway.setQueueAttributes(attributes);
        } catch(GatewayException ge) {
            hub.removeGateway(id);
            throw ge;
        }

        return gateway;
    }

    /**
     * Stops the gateways attached to a hub, then stops the hub
     * @param hub the hub to stop
     */
    private void stopHub( GatewayHub hub ) {
        for ( Gateway gateway : hub.getGateways() ) gateway.stop();
        hub.stop();
    }

    /**
     * Starts the hub and its gateways
     * @param hub the hub to start
     * @throws IOException if there is a problem starting the hub
     */
    private void startHub( GatewayHub hub ) throws IOException {
       hub.start();
       for ( Gateway gateway : hub.getGateways() ) gateway.start();
    }

}
