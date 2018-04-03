package org.aksw.sparql_integrate.ckan.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.aksw.commons.util.strings.StringUtils;
import org.aksw.sparql_integrate.ckan.domain.api.Dataset;
import org.aksw.sparql_integrate.ckan.domain.api.DatasetResource;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.trentorise.opendata.jackan.CkanClient;
import eu.trentorise.opendata.jackan.exceptions.CkanException;
import eu.trentorise.opendata.jackan.exceptions.CkanNotFoundException;
import eu.trentorise.opendata.jackan.internal.org.apache.http.HttpEntity;
import eu.trentorise.opendata.jackan.internal.org.apache.http.HttpResponse;
import eu.trentorise.opendata.jackan.internal.org.apache.http.client.methods.HttpPost;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.ContentType;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.mime.MultipartEntityBuilder;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.mime.content.FileBody;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.mime.content.StringBody;
import eu.trentorise.opendata.jackan.internal.org.apache.http.impl.client.CloseableHttpClient;
import eu.trentorise.opendata.jackan.internal.org.apache.http.impl.client.HttpClientBuilder;
import eu.trentorise.opendata.jackan.model.CkanDataset;
import eu.trentorise.opendata.jackan.model.CkanResource;

/**
 * Read CkanDataset specifications from an RDF model
 * 
 * @author raven Apr 2, 2018
 *
 */
public class CkanRdfDatasetProcessor {
	private static final Logger logger = LoggerFactory.getLogger(CkanRdfDatasetProcessor.class);

	
	protected CkanClient ckanClient;
	
	public CkanRdfDatasetProcessor(CkanClient ckanClient) {
		super();
		this.ckanClient = ckanClient;
	}


	public void process(org.apache.jena.query.Dataset sparqlDataset) {
		// List dataset descriptions
		
		Collection<Dataset> datasets = sparqlDataset.getDefaultModel().listSubjectsWithProperty(RDF.type, DCAT.Dataset)
				.mapWith(r -> r.as(Dataset.class)
				).toList();
		
		for(Dataset d : datasets) {
			System.out.println("Processing: " + d.getTitle());
			runUpload(sparqlDataset, ckanClient, d);
		}
		
	}
	
	
	public static String uploadFile(
			CkanClient ckanClient,
			String datasetName,
			String resourceId,
			//String resourceName,
			boolean isResourceCreationRequired,
			String filename)
	{
		Path path = Paths.get(filename);
		logger.info("Updating ckan resource " + resourceId + " with content from " + path.toAbsolutePath());

		String apiKey = ckanClient.getCkanToken();
		String HOST = ckanClient.getCatalogUrl();// "http://ckan.host.com";

		try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {

			// Ideally I'd like to use nio.Path instead of File but apparently the http
			// client library does not support it(?)
			File file = path.toFile();

			// SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
			// String date=dateFormatGmt.format(new Date());

			HttpPost postRequest;
			HttpEntity reqEntity = MultipartEntityBuilder.create()
					.addPart("id", new StringBody(resourceId, ContentType.TEXT_PLAIN))
					//.addPart("name", new StringBody(resourceName, ContentType.TEXT_PLAIN))
					.addPart("package_id", new StringBody(datasetName, ContentType.TEXT_PLAIN))
					.addPart("upload", new FileBody(file, ContentType.DEFAULT_BINARY, "test.nt")) // , ContentType.APPLICATION_OCTET_STREAM))
					// .addPart("file", cbFile)
					// .addPart("url",new StringBody("path/to/save/dir", ContentType.TEXT_PLAIN))
					// .addPart("comment",new StringBody("comments",ContentType.TEXT_PLAIN))
					// .addPart("notes", new StringBody("notes",ContentType.TEXT_PLAIN))
					// .addPart("author",new StringBody("AuthorName",ContentType.TEXT_PLAIN))
					// .addPart("author_email",new StringBody("AuthorEmail",ContentType.TEXT_PLAIN))
					// .addPart("title",new StringBody("title",ContentType.TEXT_PLAIN))
					// .addPart("description",new StringBody("file
					// Desc"+date,ContentType.TEXT_PLAIN))
					.build();

			String url = false//isResourceCreationRequired
					? HOST + "/api/action/resource_create"
					: HOST + "/api/action/resource_update";
			
			postRequest = new HttpPost(url);
			
			postRequest.setEntity(reqEntity);
			postRequest.setHeader("Authorization", apiKey);
			// postRequest.setHeader("X-CKAN-API-Key", myApiKey);

			HttpResponse response = httpclient.execute(postRequest);
			int statusCode = response.getStatusLine().getStatusCode();			
			String status =  new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
					.lines().collect(Collectors.joining("\n"));

			logger.info("Upload status: " + statusCode + "\n" + status);

			return status;
		} catch (IOException e) {
			throw new CkanException(e.getMessage(), ckanClient, e);
		}
	}
	
	public void runUpload(org.apache.jena.query.Dataset sparqlDataset, CkanClient ckanClient, Dataset dataset) {
		String datasetName = dataset.getName();
		CkanDataset remoteCkanDataset;
		
		boolean isDatasetCreationRequired = false;
		try {
			remoteCkanDataset = ckanClient.getDataset(datasetName);
		} catch(CkanNotFoundException e) {
			System.out.println("Dataset does not yet exist");
			remoteCkanDataset = new CkanDataset();
			isDatasetCreationRequired = true;
		} catch(CkanException e) {
			// TODO Maybe the dataset was deleted
			remoteCkanDataset = new CkanDataset();
			isDatasetCreationRequired = true;
		}

		System.out.println("Before: " + remoteCkanDataset);

		// Update existing attributes with non-null values
		//dataset.getName(datasetId);
		Optional.ofNullable(dataset.getName()).ifPresent(remoteCkanDataset::setName);
		Optional.ofNullable(dataset.getTitle()).ifPresent(remoteCkanDataset::setTitle);
		Optional.ofNullable(dataset.getDescription()).ifPresent(remoteCkanDataset::setNotes);
		
		
		System.out.println("After: " + remoteCkanDataset);
		
		if(isDatasetCreationRequired) {
			remoteCkanDataset = ckanClient.createDataset(remoteCkanDataset);
		} else {
			remoteCkanDataset = ckanClient.updateDataset(remoteCkanDataset);
		}
		
		for(DatasetResource res : dataset.getResources()) {

			CkanResource remoteCkanResource = createOrUpdateResource(ckanClient, remoteCkanDataset, dataset, res);

			// Check if there is a graph in the dataset that matches the distribution
			String distributionGraphUri = res.getURI();
			Model model = sparqlDataset.getNamedModel(distributionGraphUri);
			
			System.out.println("Distribution in graph: " + distributionGraphUri);
			

			if(model != null) {
				try {
					Path path = Files.createTempFile(StringUtils.urlEncode("data-" + distributionGraphUri), ".nt");
					try {				
						RDFDataMgr.write(Files.newOutputStream(path), model, RDFFormat.NTRIPLES);

						uploadFile(ckanClient, remoteCkanDataset.getName(), remoteCkanResource.getId(), false, path.toString());
					} finally {
						Files.delete(path);
					}
					
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				
				System.out.println("YAY model");
			}
			
			
			//System.out.println("Resource: " + dr);
			//break;
		}
		//cka
	}
	
	/**
	 * Create or update the appropriate resource among the ones in a given dataset
	 * 
	 * @param ckanClient
	 * @param dataset
	 * @param res
	 * @throws IOException 
	 */
	public CkanResource createOrUpdateResource(CkanClient ckanClient, CkanDataset ckanDataset, Dataset dataset, DatasetResource res) {
		Map<String, CkanResource> nameToCkanResource = Optional.ofNullable(ckanDataset.getResources()).orElse(Collections.emptyList()).stream()
				//.collect(Collectors.toMap(r -> Optional.ofNullable(r.getId()).orElse(r.getName()), x -> x));
				.collect(Collectors.toMap(r -> Optional.ofNullable(r.getName()).orElse(r.getId()), x -> x));
		
		// Resources are required to have an ID
		String resName = Optional.ofNullable(res.getName())
				.orElseThrow(() -> new RuntimeException("resource must have a name i.e. public id"));
		
		boolean isResourceCreationRequired = false;
		CkanResource remote = nameToCkanResource.get(resName);
		
		// TODO We need a file for the resource
		
		if(remote == null) {
			isResourceCreationRequired = true;
			
			remote = new CkanResource(null, ckanDataset.getName());
		}

		
		// Update existing attributes with non-null values
		Optional.ofNullable(res.getName()).ifPresent(remote::setName);
		//Optional.ofNullable(res.getTitle()).ifPresent(remote::set);
		Optional.ofNullable(res.getDescription()).ifPresent(remote::setDescription);

		if(isResourceCreationRequired) {
			remote = ckanClient.createResource(remote);
		} else {
			remote = ckanClient.updateResource(remote);
		}		
		
		return remote;
	}
	
}
