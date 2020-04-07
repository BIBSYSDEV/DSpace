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
        return new SolrFilterResult("item.public:true");
    }

    @Override
    public boolean isShown(DSpaceItem item) {
        try {
            // If Handle or Item are not found, return false
            String handle = DSpaceItem.parseHandle(item.getIdentifier());
            if (handle == null) {
                return false;
            }
            Item dspaceItem = (Item) handleService.resolveToObject(context, handle);
            if (dspaceItem == null) {
                return false;
            }
            final List<Bundle> originalBundle = itemService.getBundles(dspaceItem, Constants.CONTENT_BUNDLE_NAME);
            for (Bundle bundle : originalBundle) {
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
