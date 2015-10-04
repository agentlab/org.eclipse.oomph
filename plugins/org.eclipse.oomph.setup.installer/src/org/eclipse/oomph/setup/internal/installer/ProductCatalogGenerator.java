/*
 * Copyright (c) 2015 Eike Stepper (Berlin, Germany) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Eike Stepper - initial API and implementation
 */
package org.eclipse.oomph.setup.internal.installer;

import org.eclipse.oomph.base.Annotation;
import org.eclipse.oomph.base.BaseFactory;
import org.eclipse.oomph.base.util.BaseResourceFactoryImpl;
import org.eclipse.oomph.internal.setup.SetupProperties;
import org.eclipse.oomph.p2.P2Factory;
import org.eclipse.oomph.p2.Repository;
import org.eclipse.oomph.p2.Requirement;
import org.eclipse.oomph.p2.VersionSegment;
import org.eclipse.oomph.p2.core.Agent;
import org.eclipse.oomph.p2.core.P2Util;
import org.eclipse.oomph.p2.internal.core.AgentImpl;
import org.eclipse.oomph.setup.AnnotationConstants;
import org.eclipse.oomph.setup.InstallationTask;
import org.eclipse.oomph.setup.Product;
import org.eclipse.oomph.setup.ProductCatalog;
import org.eclipse.oomph.setup.ProductVersion;
import org.eclipse.oomph.setup.SetupFactory;
import org.eclipse.oomph.setup.p2.P2Task;
import org.eclipse.oomph.setup.p2.SetupP2Factory;
import org.eclipse.oomph.util.CollectionUtil;
import org.eclipse.oomph.util.IOUtil;
import org.eclipse.oomph.util.StringUtil;
import org.eclipse.oomph.util.XMLUtil;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.UniqueEList;
import org.eclipse.emf.ecore.resource.Resource;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.OSGiVersion;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointInstruction;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.query.CompoundQueryable;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.ICompositeRepository;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eike Stepper
 */
@SuppressWarnings("restriction")
public class ProductCatalogGenerator implements IApplication
{
  private static final String JAVA_VERSION_PREFIX = "addJvmArg(jvmArg:-Dosgi.requiredJavaVersion=";

  private static final String PACKAGES = "http://download.eclipse.org/technology/epp/packages";

  private static final String RELEASES = "http://download.eclipse.org/releases";

  private static final String ICON_URL_PREFIX = "http://www.eclipse.org/downloads/images/";

  private static final String ICON_DEFAULT = ICON_URL_PREFIX + "committers.png";

  private static final Map<String, String> ICONS = new HashMap<String, String>();

  private static final List<String> PRODUCT_IDS = Arrays.asList(new String[] { "epp.package.java", "epp.package.jee", "epp.package.cpp", "epp.package.php",
      "epp.package.committers", "epp.package.dsl", "epp.package.reporting", "epp.package.modeling", "epp.package.rcp", "epp.package.testing",
      "epp.package.parallel", "epp.package.automotive", "epp.package.scout", "org.eclipse.platform.ide" });

  private static final Set<String> EXCLUDED_IDS = new HashSet<String>(Arrays.asList("epp.package.mobile"));

  public Object start(IApplicationContext context) throws Exception
  {
    // if (true)
    // {
    // String completeLocation = "http://download.eclipse.org/releases/mars/201506241002";
    //
    // Product product = SetupFactory.eINSTANCE.createProduct();
    // product.setName("org.eclipse.complete");
    // product.setLabel("Eclipse IDE Complete");
    //
    // ProductVersion productVersion = SetupFactory.eINSTANCE.createProductVersion();
    // productVersion.setName("mars");
    // productVersion.setLabel("Mars");
    // product.getVersions().add(productVersion);
    //
    // P2Task p2Task = SetupP2Factory.eINSTANCE.createP2Task();
    // p2Task.setLabel("Complete");
    // productVersion.getSetupTasks().add(p2Task);
    //
    // Repository completeRepository = P2Factory.eINSTANCE.createRepository();
    // completeRepository.setURL(completeLocation);
    // p2Task.getRepositories().add(completeRepository);
    //
    // IMetadataRepositoryManager manager = getMetadataRepositoryManager();
    // IMetadataRepository repository = manager.loadRepository(new URI(completeLocation), null);
    //
    // Set<IInstallableUnit> ius = RootAnalyzer.getRootUnits(repository, null);
    // List<IInstallableUnit> rootUnits = new ArrayList<IInstallableUnit>(ius);
    // Collections.sort(rootUnits, new Comparator<IInstallableUnit>()
    // {
    // public int compare(IInstallableUnit iu1, IInstallableUnit iu2)
    // {
    // return iu1.getId().compareTo(iu2.getId());
    // }
    // });
    //
    // for (IInstallableUnit rootUnit : rootUnits)
    // {
    // Requirement requirement = P2Factory.eINSTANCE.createRequirement();
    // requirement.setName(rootUnit.getId());
    // requirement.setMatchExpression(rootUnit.getFilter());
    // p2Task.getRequirements().add(requirement);
    // }
    //
    // System.out.println();
    // System.out.println();
    //
    // Resource resource = new BaseResourceFactoryImpl().createResource(org.eclipse.emf.common.util.URI.createURI("org.eclipse.complete.setup"));
    // resource.getContents().add(product);
    // resource.save(System.out, null);
    //
    // return null;
    // }

    // luna
    // -staging mars staging-epp staging-train
    String[] arguments = (String[])context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
    org.eclipse.emf.common.util.URI outputLocation = null;
    String stagingTrain = null;
    URI stagingEPPLocation = null;
    URI stagingTrainLocation = null;
    if (arguments != null)
    {
      for (int i = 0; i < arguments.length; ++i)
      {
        String option = arguments[i];
        if ("-outputLocation".equals(option))
        {
          outputLocation = org.eclipse.emf.common.util.URI.createURI(arguments[++i]);
        }
        else if ("-staging".equals(option))
        {
          stagingTrain = arguments[++i];
          stagingEPPLocation = new URI(arguments[++i]);
          stagingTrainLocation = new URI(arguments[++i]);
        }
      }
    }

    ICONS.put("reporting", ICON_URL_PREFIX + "birt-icon_48x48.png");
    ICONS.put("cpp", ICON_URL_PREFIX + "cdt.png");
    ICONS.put("automotive", ICON_URL_PREFIX + "classic.jpg");
    ICONS.put("standard", ICON_URL_PREFIX + "committers.png");
    ICONS.put("committers", ICON_URL_PREFIX + "committers.png");
    ICONS.put("dsl", ICON_URL_PREFIX + "dsl-package_42.png");
    ICONS.put("java", ICON_URL_PREFIX + "java.png");
    ICONS.put("jee", ICON_URL_PREFIX + "javaee.png");
    // ICONS.put("?", ICON_URL_PREFIX+"JRebel-42x42-dark.png");
    ICONS.put("modeling", ICON_URL_PREFIX + "modeling.png");
    ICONS.put("parallel", ICON_URL_PREFIX + "parallel.png");
    ICONS.put("php", ICON_URL_PREFIX + "php.png");
    ICONS.put("rcp", ICON_URL_PREFIX + "rcp.png");
    ICONS.put("scout", ICON_URL_PREFIX + "scout.jpg");
    ICONS.put("testing", ICON_URL_PREFIX + "testing.png");
    ICONS.put("mobile", ICON_URL_PREFIX + "mobile.jpg");

    generate(outputLocation, stagingTrain, stagingEPPLocation, stagingTrainLocation);
    return null;
  }

  public void stop()
  {
  }

  private String[] getTrains()
  {
    return new String[] { "juno", "kepler", "luna", "mars", "neon" };
  }

  private String[] getRootIUs()
  {
    return new String[] { "org.eclipse.platform.feature.group", "org.eclipse.rcp.feature.group", "org.eclipse.jdt.feature.group",
        "org.eclipse.pde.feature.group" };
  }

  private boolean isLatestReleased()
  {
    return false;
  }

  private boolean testNewUnreleasedProduct()
  {
    return false;
  }

  public void generate(org.eclipse.emf.common.util.URI outputLocation, String stagingTrain, URI stagingEPPLocation, URI stagingTrainLocation)
  {
    final String[] TRAINS = getTrains();
    final String LATEST_TRAIN = TRAINS[TRAINS.length - 1];
    final boolean LATEST_RELEASED = !testNewUnreleasedProduct() && isLatestReleased();

    try
    {
      ProductCatalog productCatalog = SetupFactory.eINSTANCE.createProductCatalog();
      productCatalog.setName("org.eclipse.products");
      productCatalog.setLabel("Eclipse.org");

      Annotation annotation = BaseFactory.eINSTANCE.createAnnotation(AnnotationConstants.ANNOTATION_BRANDING_INFO);
      annotation.getDetails().put(AnnotationConstants.KEY_README_PATH, "readme/readme_eclipse.html");
      productCatalog.getAnnotations().add(annotation);

      InstallationTask installationTask = SetupFactory.eINSTANCE.createInstallationTask();
      installationTask.setID("installation");
      productCatalog.getSetupTasks().add(installationTask);

      IMetadataRepositoryManager manager = getMetadataRepositoryManager();

      Requirement oomphRequirement = P2Factory.eINSTANCE.createRequirement("org.eclipse.oomph.setup.feature.group");

      Repository oomphRepository = P2Factory.eINSTANCE.createRepository("${" + SetupProperties.PROP_UPDATE_URL + "}");
      String emfRepositoryLocation = trimEmptyTrailingSegment(
          loadLatestRepository(manager, null, new URI("http://download.eclipse.org/modeling/emf/emf/updates/2.10.x/core")).getLocation()).toString();

      P2Task p2Task = SetupP2Factory.eINSTANCE.createP2Task();
      p2Task.getRequirements().add(oomphRequirement);
      p2Task.getRepositories().add(oomphRepository);
      productCatalog.getSetupTasks().add(p2Task);

      final Map<String, IMetadataRepository> eppMetaDataRepositories = new HashMap<String, IMetadataRepository>();
      final Map<String, List<TrainAndVersion>> trainsAndVersions = new HashMap<String, List<TrainAndVersion>>();
      final Map<String, String> labels = new HashMap<String, String>();

      for (final String train : TRAINS)
      {
        URI originalEPPURI = new URI(PACKAGES + "/" + train);
        URI eppURI = originalEPPURI;
        System.out.print(eppURI);
        boolean isStaging = train.equals(stagingTrain);
        if (isStaging)
        {
          System.out.print(" -> " + stagingEPPLocation);
        }

        System.out.flush();

        URI effectiveEPPURI = isStaging ? stagingEPPLocation : eppURI;
        IMetadataRepository eppMetaDataRepository = manager.loadRepository(effectiveEPPURI, null);
        IMetadataRepository latestEPPMetaDataRepository = getLatestRepository(manager, eppMetaDataRepository);
        if (latestEPPMetaDataRepository != eppMetaDataRepository)
        {
          URI latestLocation = latestEPPMetaDataRepository.getLocation();
          System.out.print(" -> " + latestLocation);
          org.eclipse.emf.common.util.URI relativeLocation = org.eclipse.emf.common.util.URI.createURI(latestLocation.toString())
              .deresolve(org.eclipse.emf.common.util.URI.createURI(effectiveEPPURI.toString()).appendSegment(""));
          if (relativeLocation.isRelative())
          {
            URI actualLatestEPPURI = new URI(
                org.eclipse.emf.common.util.URI.createURI(eppURI.toString()).appendSegments(relativeLocation.segments()).toString());
            try
            {
              manager.loadRepository(actualLatestEPPURI, null);
              System.out.print(" -> " + actualLatestEPPURI);
              eppURI = trimEmptyTrailingSegment(actualLatestEPPURI);
            }
            catch (Throwable throwable)
            {
              // Ignore;

            }
          }
        }

        System.out.println();

        eppMetaDataRepositories.put(train, eppMetaDataRepository);

        Map<String, IInstallableUnit> ius = new HashMap<String, IInstallableUnit>();

        URI releaseURI = new URI(RELEASES + "/" + train);
        System.out.print(releaseURI);

        IMetadataRepository releaseMetaDataRepository = loadLatestRepository(manager, originalEPPURI, isStaging ? stagingTrainLocation : releaseURI);
        releaseURI = trimEmptyTrailingSegment(releaseMetaDataRepository.getLocation());
        System.out.println(" -> " + releaseURI);

        Set<String> requirements = new HashSet<String>();

        for (IInstallableUnit iu : P2Util.asIterable(eppMetaDataRepository.query(QueryUtil.createLatestIUQuery(), null)))
        {
          String fragment = iu.getProperty("org.eclipse.equinox.p2.type.fragment");
          if ("true".equals(fragment))
          {
            continue;
          }

          String label = iu.getProperty("org.eclipse.equinox.p2.name");
          if (label == null || label.startsWith("%") || label.equals("Uncategorized"))
          {
            // Ensure that this is removed later,
            // but that the requirements are still processed for filtering roots.
            requirements.add(iu.getId());
          }

          String id = iu.getId();
          if ("epp.package.standard".equals(id))
          {
            label = "Eclipse Standard";
          }

          IInstallableUnit existingIU = ius.get(id);
          if (existingIU == null)
          {
            ius.put(id, iu);
            labels.put(id, label);
          }
        }

        for (IInstallableUnit iu : ius.values())
        {
          for (IRequirement requirement : iu.getRequirements())
          {
            if (requirement instanceof IRequiredCapability)
            {
              IRequiredCapability capability = (IRequiredCapability)requirement;
              if ("org.eclipse.equinox.p2.iu".equals(capability.getNamespace()))
              {
                requirements.add(capability.getName());
              }
            }
          }
        }

        ius.keySet().removeAll(requirements);

        for (IInstallableUnit iu : P2Util
            .asIterable(releaseMetaDataRepository.query(QueryUtil.createLatestQuery(QueryUtil.createIUQuery("org.eclipse.platform.ide")), null)))
        {
          String id = iu.getId();
          String label = iu.getProperty("org.eclipse.equinox.p2.name");
          ius.put(id, iu);
          labels.put(id, label);
        }

        for (Map.Entry<String, IInstallableUnit> entry : ius.entrySet())
        {
          String id = entry.getKey();
          String label = labels.get(id);
          IInstallableUnit iu = entry.getValue();
          final Version version = iu.getVersion();
          System.out.println("  " + label + "  --  " + id + " " + version);

          List<TrainAndVersion> list = trainsAndVersions.get(id);
          if (list == null)
          {
            list = new ArrayList<TrainAndVersion>();
            trainsAndVersions.put(id, list);
          }

          Map<String, Set<IInstallableUnit>> versionIUs = new HashMap<String, Set<IInstallableUnit>>();
          gatherReleaseIUs(versionIUs, iu, releaseMetaDataRepository, eppMetaDataRepository);
          filterRoots(versionIUs);

          list.add(new TrainAndVersion(train, version, releaseURI, eppURI, versionIUs));
        }

        System.out.println();
      }

      if (testNewUnreleasedProduct())
      {
        List<TrainAndVersion> list = new ArrayList<TrainAndVersion>();
        list.add(new TrainAndVersion("mars", null, null, null, null));

        trainsAndVersions.put("test.product", list);
        labels.put("test.product", "Eclipse Test Product");
      }

      System.out.println("#################################################################################################################");
      System.out.println();

      List<String> ids = new ArrayList<String>(trainsAndVersions.keySet());
      Collections.sort(ids, new Comparator<String>()
      {
        public int compare(String id1, String id2)
        {
          int result = getLatestTrain(id2) - getLatestTrain(id1);
          if (result == 0)
          {
            String label1 = StringUtil.safe(labels.get(id1));
            String label2 = StringUtil.safe(labels.get(id2));
            result = label1.compareTo(label2);
          }

          return result;
        }

        private int getLatestTrain(String id)
        {
          List<TrainAndVersion> list = trainsAndVersions.get(id);
          TrainAndVersion lastEntry = list.get(list.size() - 1);
          String lastTrain = lastEntry.getTrain();

          for (int i = 0; i < TRAINS.length; i++)
          {
            String train = TRAINS[i];
            if (train == lastTrain)
            {
              return i;
            }
          }

          throw new IllegalStateException();
        }
      });

      final EList<Product> products = productCatalog.getProducts();
      for (String id : ids)
      {
        String label = labels.get(id);
        String p2TaskLabel = label;

        List<TrainAndVersion> list = trainsAndVersions.get(id);
        int size = list.size();

        TrainAndVersion latestTrainAndVersion = list.get(size - 1);
        String latestTrain = latestTrainAndVersion.getTrain();
        String latestTrainLabel = getTrainLabel(latestTrain);
        Version latestVersion = latestTrainAndVersion.getVersion();
        Map<String, Set<IInstallableUnit>> latestTrainsIUs = latestTrainAndVersion.getIUs();

        // if (latestTrain != LATEST_TRAIN)
        // {
        // label += " (discontinued after " + latestTrainLabel + ")";
        // }

        boolean latestUnreleased = latestTrain == LATEST_TRAIN && !LATEST_RELEASED;
        // if (latestUnreleased && size == 1)
        // {
        // label += " (unreleased before " + latestTrainLabel + ")";
        // }

        System.out.println(label + " (" + id + ")");

        Product product = SetupFactory.eINSTANCE.createProduct();
        product.setName(id);
        product.setLabel(label);
        attachBrandingInfos(product);
        products.add(product);

        addProductVersion(product, latestVersion, VersionSegment.MAJOR, latestTrainAndVersion.getTrainURI(), latestTrainAndVersion.getEPPURI(), latestTrain,
            eppMetaDataRepositories, "latest", "Latest (" + latestTrainLabel + ")", p2TaskLabel, latestTrainsIUs, emfRepositoryLocation);

        if (!latestUnreleased || size != 1)
        {
          int offset = 1;
          if (latestUnreleased && size > 1)
          {
            ++offset;
          }

          TrainAndVersion releasedTrainAndVersion = list.get(size - offset);
          String releasedTrain = releasedTrainAndVersion.getTrain();
          String releasedTrainLabel = getTrainLabel(releasedTrain);
          Version releasedVersion = releasedTrainAndVersion.getVersion();

          addProductVersion(product, releasedVersion, VersionSegment.MINOR, releasedTrainAndVersion.getTrainURI(), releasedTrainAndVersion.getEPPURI(),
              releasedTrain, eppMetaDataRepositories, "latest.released", "Latest Release (" + releasedTrainLabel + ")", p2TaskLabel,
              releasedTrainAndVersion.getIUs(), emfRepositoryLocation);
        }

        for (int i = 0; i < size; i++)
        {
          TrainAndVersion entry = list.get(size - i - 1);
          String train = entry.getTrain();
          String trainLabel = getTrainLabel(train);
          Version version = entry.getVersion();

          addProductVersion(product, version, VersionSegment.MINOR, entry.getTrainURI(), entry.getEPPURI(), train, eppMetaDataRepositories, train, trainLabel,
              p2TaskLabel, entry.getIUs(), emfRepositoryLocation);
        }

        System.out.println();
      }

      final List<String> productIDs = new UniqueEList<String>(PRODUCT_IDS);
      for (Product product : products)
      {
        productIDs.add(product.getName());
      }

      ECollections.sort(products, new Comparator<Product>()
      {
        public int compare(Product product1, Product product2)
        {
          int index1 = productIDs.indexOf(product1.getName());
          int index2 = productIDs.indexOf(product2.getName());
          return index1 - index2;
        }
      });

      System.out.println("#################################################################################################################");
      System.out.println();

      checkVersionRanges(productCatalog);
      postProcess(productCatalog);

      Resource resource = new BaseResourceFactoryImpl()
          .createResource(outputLocation == null ? org.eclipse.emf.common.util.URI.createURI("org.eclipse.products.setup") : outputLocation);
      resource.getContents().add(productCatalog);
      // resource.save(System.out, null);

      if (outputLocation != null)
      {
        resource.save(null);
      }
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }

  private URI trimEmptyTrailingSegment(URI uri) throws URISyntaxException
  {
    String value = uri.toString();
    if (value.endsWith("/"))
    {
      return new URI(value.substring(0, value.length() - 1));
    }

    return uri;
  }

  private IMetadataRepositoryManager getMetadataRepositoryManager() throws IOException
  {
    File agentLocation = File.createTempFile("test-", "-agent");
    agentLocation.delete();
    agentLocation.mkdirs();

    Agent agent = new AgentImpl(null, agentLocation);
    IMetadataRepositoryManager manager = agent.getMetadataRepositoryManager();
    return manager;
  }

  private IMetadataRepository getLatestRepository(IMetadataRepositoryManager manager, IMetadataRepository repository)
      throws URISyntaxException, ProvisionException
  {
    IMetadataRepository result = repository;
    if (!isLatestReleased() && repository instanceof ICompositeRepository<?>)
    {
      ICompositeRepository<?> compositeRepository = (ICompositeRepository<?>)repository;
      long latest = Integer.MIN_VALUE;
      for (URI childURI : compositeRepository.getChildren())
      {
        childURI = trimEmptyTrailingSegment(childURI);
        IMetadataRepository childRepository = manager.loadRepository(childURI, null);
        String value = childRepository.getProperties().get(IRepository.PROP_TIMESTAMP);
        long timestamp = Long.parseLong(value);
        if (timestamp > latest)
        {
          result = childRepository;
          latest = timestamp;
        }
      }
    }

    return result;
  }

  private IMetadataRepository loadLatestRepository(IMetadataRepositoryManager manager, URI eppURI, URI releaseURI) throws URISyntaxException, ProvisionException
  {
    IMetadataRepository releaseMetaDataRepository = manager.loadRepository(releaseURI, null);
    IMetadataRepository result = releaseMetaDataRepository;
    if (releaseMetaDataRepository instanceof ICompositeRepository<?>)
    {
      ICompositeRepository<?> compositeRepository = (ICompositeRepository<?>)releaseMetaDataRepository;
      long latest = Integer.MIN_VALUE;
      for (URI childURI : compositeRepository.getChildren())
      {
        childURI = trimEmptyTrailingSegment(childURI);
        if (!childURI.equals(eppURI))
        {
          IMetadataRepository childRepository = manager.loadRepository(childURI, null);
          String value = childRepository.getProperties().get(IRepository.PROP_TIMESTAMP);
          long timestamp = Long.parseLong(value);
          if (timestamp > latest)
          {
            result = childRepository;
            latest = timestamp;
          }
        }
      }
    }

    return result;
  }

  private void checkVersionRanges(ProductCatalog productCatalog)
  {
    if (!isLatestReleased())
    {
      for (Product product : productCatalog.getProducts())
      {
        EList<ProductVersion> versions = product.getVersions();
        if (versions.size() > 3)
        {
          ProductVersion latestReleaseVersion = versions.get(1);
          ProductVersion latestDevelopmentVersion = versions.get(2);

          P2Task latestReleaseP2Task = (P2Task)latestReleaseVersion.getSetupTasks().get(0);
          P2Task latestDevelopmentP2Task = (P2Task)latestDevelopmentVersion.getSetupTasks().get(0);

          for (Requirement developmentRequirement : latestDevelopmentP2Task.getRequirements())
          {
            String name = developmentRequirement.getName();
            for (Requirement releaseRequirement : latestReleaseP2Task.getRequirements())
            {
              if (name.equals(releaseRequirement.getName()))
              {
                VersionRange developmentVersionRange = developmentRequirement.getVersionRange();
                VersionRange releaseVersionRange = releaseRequirement.getVersionRange();
                if (developmentVersionRange.equals(releaseVersionRange))
                {
                  OSGiVersion minimum = (OSGiVersion)developmentVersionRange.getMinimum();
                  OSGiVersion maximum = (OSGiVersion)developmentVersionRange.getMaximum();
                  int major = minimum.getMajor();
                  if (major == maximum.getMajor())
                  {
                    developmentRequirement.setVersionRange(new VersionRange(minimum, true, Version.createOSGi(major, maximum.getMinor() + 1, 0), false));
                  }
                }

                break;
              }
            }
          }
        }
      }
    }
  }

  private void postProcess(ProductCatalog productCatalog)
  {
    for (Iterator<Product> it = productCatalog.getProducts().iterator(); it.hasNext();)
    {
      Product product = it.next();
      String id = product.getName();

      if (EXCLUDED_IDS.contains(id))
      {
        it.remove();
      }

      if ("epp.package.standard".equals(id))
      {
        for (ProductVersion version : product.getVersions())
        {
          if (version.getLabel().contains("Mars"))
          {
            P2Task task = (P2Task)version.getSetupTasks().get(0);
            Requirement requirement = task.getRequirements().get(0);
            requirement.setName("epp.package.committers");
          }
        }
      }
    }
  }

  private void gatherReleaseIUs(Map<String, Set<IInstallableUnit>> releaseIUs, IInstallableUnit iu, IMetadataRepository releaseMetaDataRepository,
      IMetadataRepository eppMetaDataRepository)
  {
    for (IRequirement requirement : iu.getRequirements())
    {
      if (requirement instanceof IRequiredCapability)
      {
        IRequiredCapability capability = (IRequiredCapability)requirement;
        String capabilityName = capability.getName();
        if (capabilityName.endsWith(Requirement.FEATURE_SUFFIX))
        {
          @SuppressWarnings("unchecked")
          IQueryable<IInstallableUnit> queriable = new CompoundQueryable<IInstallableUnit>(
              new IQueryable[] { releaseMetaDataRepository, eppMetaDataRepository });
          IQuery<IInstallableUnit> query = QueryUtil.createIUQuery(capabilityName, capability.getRange());
          for (IInstallableUnit requiredIU : P2Util.asIterable(queriable.query(query, null)))
          {
            if (CollectionUtil.add(releaseIUs, capabilityName, requiredIU))
            {
              gatherReleaseIUs(releaseIUs, requiredIU, releaseMetaDataRepository, eppMetaDataRepository);
            }
          }
        }
      }
    }
  }

  private void filterRoots(Map<String, Set<IInstallableUnit>> releaseIUs)
  {
    for (Iterator<Map.Entry<String, Set<IInstallableUnit>>> it = releaseIUs.entrySet().iterator(); it.hasNext();)
    {
      String id = it.next().getKey();
      if (!id.endsWith("feature.group") || id.startsWith("org.eclipse.epp") || id.endsWith("epp.feature.group"))
      {
        it.remove();
      }
    }
  }

  private void addProductVersion(Product product, Version version, VersionSegment versionSegment, URI trainURI, URI eppURI, String train,
      Map<String, IMetadataRepository> eppMetaDataRepositories, String name, String label, String p2TaskLabel, Map<String, Set<IInstallableUnit>> ius,
      String emfRepositoryLocation)
  {
    System.out.print("  " + label);

    ProductVersion productVersion = SetupFactory.eINSTANCE.createProductVersion();
    productVersion.setName(name);
    productVersion.setLabel(label);
    product.getVersions().add(productVersion);

    String productName = product.getName();
    VersionRange versionRange = createVersionRange(version, versionSegment);

    Requirement requirement = P2Factory.eINSTANCE.createRequirement();
    requirement.setName(productName);
    requirement.setVersionRange(versionRange);

    Repository packageRepository = P2Factory.eINSTANCE.createRepository();
    packageRepository.setURL(eppURI.toString());

    Repository releaseRepository = P2Factory.eINSTANCE.createRepository();
    releaseRepository.setURL(trainURI.toString());

    P2Task p2Task = SetupP2Factory.eINSTANCE.createP2Task();
    p2Task.setLabel(p2TaskLabel + " (" + getTrainLabel(train) + ")");
    p2Task.getRequirements().add(requirement);
    addRootIURequirements(p2Task.getRequirements(), versionSegment, ius);

    if (!"org.eclipse.platform.ide".equals(name))
    {
      p2Task.getRepositories().add(packageRepository);
    }

    p2Task.getRepositories().add(releaseRepository);

    if (train.compareTo("luna") < 0)
    {
      Repository emfRepository = P2Factory.eINSTANCE.createRepository(emfRepositoryLocation);
      p2Task.getRepositories().add(emfRepository);
    }

    productVersion.getSetupTasks().add(p2Task);

    String idPrefix = "tooling" + productName + ".ini.";

    Version maxJavaVersion = null;
    if (train.compareTo("neon") >= 0)
    {
      maxJavaVersion = Version.create("1.8.0");
    }

    IMetadataRepository eppMetaDataRepository = eppMetaDataRepositories.get(train);
    if (eppMetaDataRepository != null)
    {
      for (IInstallableUnit iu : P2Util.asIterable(eppMetaDataRepository.query(QueryUtil.createIUAnyQuery(), null)))
      {
        String id = iu.getId();
        if (id.startsWith(idPrefix) && iu.getVersion().equals(version))
        {
          Collection<ITouchpointData> touchpointDatas = iu.getTouchpointData();
          if (touchpointDatas != null)
          {
            for (ITouchpointData touchpointData : touchpointDatas)
            {
              ITouchpointInstruction instruction = touchpointData.getInstruction("configure");
              if (instruction != null)
              {
                String body = instruction.getBody();
                if (body != null)
                {
                  int pos = body.indexOf(JAVA_VERSION_PREFIX);
                  if (pos != -1)
                  {
                    pos += JAVA_VERSION_PREFIX.length();
                    Version javaVersion = Version.parseVersion(body.substring(pos, body.indexOf(')', pos)));
                    if (maxJavaVersion == null || maxJavaVersion.compareTo(javaVersion) < 0)
                    {
                      maxJavaVersion = javaVersion;
                    }

                    // IMatchExpression<IInstallableUnit> filter = iu.getFilter();
                    // Object[] parameters = filter.getParameters();
                    // if (parameters != null)
                    // {
                    // for (Object parameter : parameters)
                    // {
                    // if (parameter instanceof IFilterExpression)
                    // {
                    // IFilterExpression filterExpression = (IFilterExpression)parameter;
                    // System.out.println(" " + filterExpression.toString() + " --> " + javaVersion);
                    // }
                    // }
                    // }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (maxJavaVersion != null)
    {
      String javaVersion = maxJavaVersion.toString();
      while (javaVersion.endsWith(".0"))
      {
        javaVersion = javaVersion.substring(0, javaVersion.length() - 2);
      }

      productVersion.setRequiredJavaVersion(javaVersion);
      System.out.print(" --> Java " + javaVersion);
    }

    System.out.println();
  }

  private VersionRange createVersionRange(Version version, VersionSegment versionSegment)
  {
    VersionRange versionRange = P2Factory.eINSTANCE.createVersionRange(version, versionSegment);
    if (versionSegment == VersionSegment.MAJOR)
    {
      // When expanding to major, we only want to expand the upper bound to major and the lower bound to minor.
      VersionRange minorVersionRange = P2Factory.eINSTANCE.createVersionRange(version, VersionSegment.MINOR);
      versionRange = new VersionRange(minorVersionRange.getMinimum(), true, versionRange.getMaximum(), false);
    }

    return versionRange;
  }

  private void addRootIURequirements(EList<Requirement> requirements, VersionSegment versionSegment, Map<String, Set<IInstallableUnit>> ius)
  {
    for (String iu : getRootIUs())
    {
      Set<IInstallableUnit> rootIUs = ius.get(iu);
      if (rootIUs != null)
      {
        VersionRange range = null;
        for (IInstallableUnit rootIU : rootIUs)
        {
          Version version = rootIU.getVersion();
          VersionRange versionRange = createVersionRange(version, versionSegment);
          if (range == null)
          {
            range = versionRange;
          }
        }

        if (range != null)
        {
          Requirement requirement = P2Factory.eINSTANCE.createRequirement();
          requirement.setName(iu);
          requirement.setVersionRange(range);
          requirements.add(requirement);
        }
      }
    }
  }

  private String getTrainLabel(String train)
  {
    return Character.toString((char)(train.charAt(0) + 'A' - 'a')) + train.substring(1);
  }

  private void attachBrandingInfos(final Product product)
  {
    String name = product.getName();
    if (name.equals("org.eclipse.platform.ide"))
    {
      product.setDescription("This package contains the absolute minimal IDE, suitable only as a base for installing other tools.");
      return;
    }

    if (name.startsWith("epp.package."))
    {
      name = name.substring("epp.package.".length());
    }

    final String staticIconURL = ICONS.get(name);
    if (staticIconURL != null)
    {
      addImageURI(product, staticIconURL);
    }

    String[] trains = getTrains();
    for (int i = trains.length; i >= 0; --i)
    {
      InputStream in = null;

      try
      {
        String branch = i == trains.length ? "master" : trains[i].toUpperCase();
        String url = "http://git.eclipse.org/c/epp/org.eclipse.epp.packages.git/plain/packages/org.eclipse.epp.package." + name + ".feature/epp.website.xml"
            + "?h=" + branch;
        in = new URL(url).openStream();
        System.out.println(url);

        DocumentBuilder documentBuilder = XMLUtil.createDocumentBuilder();
        Element rootElement = XMLUtil.loadRootElement(documentBuilder, in);
        XMLUtil.handleElementsByTagName(rootElement, "packageMetaData", new XMLUtil.ElementHandler()
        {
          public void handleElement(Element element) throws Exception
          {
            if (staticIconURL != null)
            {
              System.out.println(staticIconURL);
            }
            else
            {
              String iconurl = element.getAttribute("iconurl");
              if (iconurl != null)
              {
                addImageURI(product, iconurl);
                System.out.println(iconurl);
              }
            }

            XMLUtil.handleChildElements(element, new XMLUtil.ElementHandler()
            {
              public void handleElement(Element childElement) throws Exception
              {
                String localName = childElement.getLocalName();
                if ("description".equals(localName))
                {
                  String description = childElement.getTextContent();
                  if (description != null)
                  {
                    product.setDescription(description.trim());
                  }
                }
              }
            });
          }
        });

        return;
      }
      catch (FileNotFoundException ex)
      {
        System.out.println(ex.getMessage() + " (FAILED)");
      }
      catch (Exception ex)
      {
        ex.printStackTrace();
      }
      finally
      {
        IOUtil.closeSilent(in);
      }
    }

    if (staticIconURL != null)
    {
      System.out.println(staticIconURL);
    }
  }

  private void addImageURI(Product product, String imageURI)
  {
    if (ICON_DEFAULT.equals(imageURI))
    {
      return;
    }

    // imageURI = IOUtil.encodeImageData(imageURI);

    EMap<String, String> brandingInfos = getBrandingInfos(product);
    if (!brandingInfos.containsKey(AnnotationConstants.KEY_IMAGE_URI))
    {
      brandingInfos.put(AnnotationConstants.KEY_IMAGE_URI, imageURI);
    }
  }

  private EMap<String, String> getBrandingInfos(Product product)
  {
    Annotation annotation = product.getAnnotation(AnnotationConstants.ANNOTATION_BRANDING_INFO);
    if (annotation == null)
    {
      annotation = BaseFactory.eINSTANCE.createAnnotation(AnnotationConstants.ANNOTATION_BRANDING_INFO);
      product.getAnnotations().add(annotation);
    }

    return annotation.getDetails();
  }

  /**
   * @author Eike Stepper
   */
  private static final class TrainAndVersion
  {
    private final String train;

    private final Version version;

    private final Map<String, Set<IInstallableUnit>> ius;

    private final URI trainURI;

    private URI eppURI;

    public TrainAndVersion(String train, Version version, URI trainURI, URI eppURI, Map<String, Set<IInstallableUnit>> ius)
    {
      this.train = train;
      this.version = version;
      this.trainURI = trainURI;
      this.eppURI = eppURI;
      this.ius = ius;
    }

    public String getTrain()
    {
      return train;
    }

    public Version getVersion()
    {
      return version;
    }

    public URI getTrainURI()
    {
      return trainURI;
    }

    public URI getEPPURI()
    {
      return eppURI;

    }

    public Map<String, Set<IInstallableUnit>> getIUs()
    {
      return ius;
    }
  }
}
