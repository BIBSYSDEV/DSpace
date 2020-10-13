/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.harvest.service.HarvestSchedulingService;
import org.dspace.harvest.service.HarvestedCollectionService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Service implementation for the scheduling of harvesting tasks.
 * This class is responsible for all business logic calls for the harvesting tasks and is autowired by spring
 * This class should never be accessed directly.
 *
 * @author kevinvandevelde at atmire.com
 */
public class HarvestSchedulingServiceImpl implements HarvestSchedulingService {

    /* The main harvesting thread */
	protected HarvestScheduler harvester;
    protected Thread mainHarvestThread;
    protected HarvestScheduler harvestScheduler;

    @Autowired(required = true)
    protected HarvestedCollectionService harvestedCollectionService;

    protected HarvestSchedulingServiceImpl()
    {

    }

    @Override
    public synchronized void startNewScheduler() throws SQLException, AuthorizeException {
        Context c = new Context();
        harvestedCollectionService.exists(c);
        c.complete();

        if (mainHarvestThread != null && harvester != null) {
                stopScheduler();
            }
    	harvester = new HarvestScheduler();
    	HarvestScheduler.setInterrupt(HarvestScheduler.HARVESTER_INTERRUPT_NONE);
    	mainHarvestThread = new Thread(harvester);
    	mainHarvestThread.start();
    }

    @Override
    public synchronized void stopScheduler() throws SQLException, AuthorizeException {
        synchronized(HarvestScheduler.lock) {
                HarvestScheduler.setInterrupt(HarvestScheduler.HARVESTER_INTERRUPT_STOP);
                HarvestScheduler.lock.notify();
        }
        mainHarvestThread = null;
                harvester = null;
    }

    @Override
	public void pauseScheduler() throws SQLException, AuthorizeException {
		synchronized(HarvestScheduler.lock) {
			HarvestScheduler.setInterrupt(HarvestScheduler.HARVESTER_INTERRUPT_PAUSE);
			HarvestScheduler.lock.notify();
		}
    }

    @Override
	public void resumeScheduler() throws SQLException, AuthorizeException {
		HarvestScheduler.setInterrupt(HarvestScheduler.HARVESTER_INTERRUPT_RESUME);
    }

    @Override
	public void resetScheduler() throws SQLException, AuthorizeException, IOException {
		Context context = new Context();
		List<HarvestedCollection> harvestedCollections = harvestedCollectionService.findAll(context);
    	for (HarvestedCollection hc : harvestedCollections)
    	{
    		hc.setHarvestStartTime(null);
    		hc.setHarvestStatus(HarvestedCollection.STATUS_READY);
            harvestedCollectionService.update(context, hc);
    	}
    }

    @Override
    public void setPlusOneDayScheduler() throws SQLException {
        Context context = new Context();
        List<HarvestedCollection> harvestedCollections = harvestedCollectionService.findAll(context);
        for (HarvestedCollection hc : harvestedCollections) {
            if (!(HarvestedCollection.STATUS_OAI_ERROR != hc.getHarvestStatus() ||
                    HarvestedCollection.STATUS_UNKNOWN_ERROR != hc.getHarvestStatus())) {
                Date harvestStartTime = hc.getHarvestStartTime();
                Calendar c = Calendar.getInstance();
                c.setTime(harvestStartTime);
                c.add(Calendar.DATE, 1);
                hc.setHarvestStartTime(c.getTime());
                hc.setHarvestStatus(HarvestedCollection.STATUS_READY);
                harvestedCollectionService.update(context, hc);
            }
        }
    }

}
