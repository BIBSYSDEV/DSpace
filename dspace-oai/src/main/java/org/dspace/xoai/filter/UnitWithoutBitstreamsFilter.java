package org.dspace.xoai.filter;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.xoai.data.DSpaceItem;
import org.dspace.xoai.filter.results.SolrFilterResult;

import java.sql.SQLException;
import java.util.List;

public class UnitWithoutBitstreamsFilter extends DSpaceFilter {

    private static final Logger log = LogManager.getLogger(UnitWithoutBitstreamsFilter.class);

    private static final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private static final ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    @Override
    public SolrFilterResult buildSolrQuery() {
        log.info("Unit filter is on.");
        return new SolrFilterResult("item.has_content_in_original_bundle_filter:true");
    }

    @Override
    public boolean isShown(DSpaceItem item) {
        log.info("Unit filter is on.");
        try {
            // If Handle or Item are not found, return false
            String handle = DSpaceItem.parseHandle(item.getIdentifier());
            log.info("item : " + handle);
            if (handle == null) {
                return false;
            }
            Item dspaceItem = (Item) handleService.resolveToObject(context, handle);
            if (dspaceItem == null) {
                return false;
            }
            log.info("dspaceitem found!!!");
            final List<Bundle> originalBundle = itemService.getBundles(dspaceItem, Constants.CONTENT_BUNDLE_NAME);
            log.info(Constants.CONTENT_BUNDLE_NAME + " - bundle found. no should be 1 or 0: " + originalBundle.size());
            for (Bundle bundle : originalBundle) {
                log.info("Found " + bundle.getBitstreams().size() + " bitsreams in teh bundle");
                if (!bundle.getBitstreams().isEmpty()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            log.error("Could not find out bundles to item with id: " + item.getIdentifier(), e);
        }
        return false;
    }
}
