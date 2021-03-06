/**
 * The contents of this file are subject to the license and copyright detailed
 * in the LICENSE and NOTICE files at the root of the source tree and available
 * online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.xmlworkflow.cristin;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Context;
import org.dspace.harvest.HarvestedCollection;
import org.dspace.harvest.factory.HarvestServiceFactory;
import org.dspace.harvest.service.HarvestedCollectionService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author ra
 *
 * prerequisite: Only the existing CRIStin and Inspera harvesting collections are mapped.
 *
 * This class reads the CRIStin and the Inspera harvesting collections from the database and
 * mapps the collections handle to the xmlworkflow named 'cristin' resp. 'inspera' The mapping
 * is stored in the config-file config/workflow.xml
 *
 *
 */
public class LinkCollectionToWorkflow {

    private static final Logger log = Logger.getLogger(LinkCollectionToWorkflow.class);
    private static HarvestedCollectionService harvestedCollectionService;
    private static ConfigurationService configurationService;
    private static Context context;

    public LinkCollectionToWorkflow() {
        harvestedCollectionService = HarvestServiceFactory.getInstance().getHarvestedCollectionService();
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        context = new Context();
    }

    public static void main(String[] args) {
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        harvestedCollectionService = HarvestServiceFactory.getInstance().getHarvestedCollectionService();
        context = new Context();
        final String workflowFileName = configurationService.getProperty("dspace.dir") + File.separator +
                "config" + File.separator + "workflow.xml";
        StringBuilder stringBuilder = new StringBuilder();
        try {
            generateMapping(stringBuilder);
            FileInputStream inputStream = new FileInputStream(workflowFileName);
            String stringContentOfFile = IOUtils.toString(inputStream);
            inputStream.close();
            // --- Print the final file --- //
            String patternString = "(?s)(.*?)<workflow-map>(.*?)</workflow-map>(.*)";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(stringContentOfFile);
            if (matcher.find()) {
                String theBeginning = matcher.group(1).trim();
                String theEnd = matcher.group(3).trim();
                PrintWriter fw = new PrintWriter(workflowFileName, "UTF-8");
                fw.append(theBeginning);
                fw.append(stringBuilder.toString());
                fw.append(theEnd);
                fw.close();
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateMapping(StringBuilder stringBuilder) throws SQLException {
        stringBuilder.append("\n");
        stringBuilder.append("    <workflow-map>" + "\n");
        stringBuilder.append("        <name-map collection=\"default\" workflow=\"default\"/>" + "\n");
        stringBuilder.append("        <!--<name-map collection=\"123456789/4\" workflow=\"selectSingleReviewer\"/>-->" + "\n");
        stringBuilder.append("        <!--<name-map collection=\"123456789/5\" workflow=\"scoreReview\"/>-->" + "\n");
        findCollection(stringBuilder, "cristin");
        findCollection(stringBuilder, "inspera");
        stringBuilder.append("    </workflow-map>" + "\n");
        stringBuilder.append("\n    ");
    }

    private static void findCollection(StringBuilder themeSB, String collectionType) throws SQLException {
        List<HarvestedCollection> harvestedCollections = harvestedCollectionService.findAll(context);
        for (HarvestedCollection harvestedCollection : harvestedCollections) {
            if (harvestedCollection == null || !collectionType.equalsIgnoreCase(harvestedCollection.getWorkflowProcess())) {
                continue;
            }
            final Collection collection = harvestedCollection.getCollection();
            if (collection != null) {
                String name = collection.getCommunities().get(0) == null ? "NOT FOUND" :  collection.getCommunities().get(0).getName();
                String collectionDescription = String.format("\"%s\" (%s) in community \"%s\"", collection.getName(), collection.getHandle(), name);
                System.out.printf("Mapped %s to %s-workflow%n", collectionDescription, collectionType);
                log.info("Mapped collectionDescription to collectionType-workflow");
                themeSB.append("        ")
                        .append("<name-map collection=\"").append(collection.getHandle()).append("\" workflow=\"").append(collectionType).append("\"/>")
                        .append(" <!-- Import from ").append(collectionType).append(" - ").append(collectionDescription).append(" -->").append("\n");
            }
        }
    }

}