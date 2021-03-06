package de.tudresden.slr.model.modelregistry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.resource.ResourceItemProviderAdapterFactory;

import de.tudresden.slr.model.bibtex.Document;
import de.tudresden.slr.model.bibtex.provider.BibtexItemProviderAdapterFactory;
import de.tudresden.slr.model.taxonomy.Model;
import de.tudresden.slr.model.taxonomy.util.TaxonomyStandaloneParser;

public class ModelRegistry extends Observable {
	private Document activeDocument;
	private Model activeTaxonomy;
	private AdapterFactoryEditingDomain sharedEditingDomain;
	private TaxonomyStandaloneParser taxonomyParser = new TaxonomyStandaloneParser();

	public ModelRegistry() {
		createEditingDomain();
	}

	private void createEditingDomain() {
		// Create an adapter factory that yields item providers.
		//
		ComposedAdapterFactory adapterFactory = new ComposedAdapterFactory(ComposedAdapterFactory.Descriptor.Registry.INSTANCE);

		adapterFactory.addAdapterFactory(new ResourceItemProviderAdapterFactory());
		adapterFactory.addAdapterFactory(new BibtexItemProviderAdapterFactory());
		adapterFactory.addAdapterFactory(new ReflectiveItemProviderAdapterFactory());

		// Create the command stack that will notify this editor as commands are
		// executed.
		//
		BasicCommandStack commandStack = new BasicCommandStack();

		// Create the editing domain with a special command stack.
		//
		sharedEditingDomain = new AdapterFactoryEditingDomain(adapterFactory,commandStack, new HashMap<Resource, Boolean>());
	}

	public Optional<AdapterFactoryEditingDomain> getEditingDomain() {
		return sharedEditingDomain == null ? Optional.empty() : Optional.of(sharedEditingDomain);
	}

	public void setEditingDomain(AdapterFactoryEditingDomain editingDomain) {
		if (sharedEditingDomain != editingDomain) {
			sharedEditingDomain = editingDomain;
			setChanged();
			notifyObservers(sharedEditingDomain);
		}
	}

	public Optional<Document> getActiveDocument() {
		return activeDocument == null ? Optional.empty() : Optional.of(activeDocument);
	}

	public void setActiveDocument(Document document) {
		if (activeDocument != document) {
			Optional<Model> currentModel = getActiveTaxonomy();
			Model model =  taxonomyParser.parseTaxonomyFile(getTaxonomyFileInProject(document.eResource()));
			if(currentModel.isPresent() && !model.getResource().getURI().equals(currentModel.get().eResource().getURI()))
				setActiveTaxonomy(model);
			
			activeDocument = document;
			setChanged();
			notifyObservers(activeDocument);
		}
	}

	public void setTaxonomyFile(IFile taxonomyFile){
		Model model =  taxonomyParser.parseTaxonomyFile(taxonomyFile);
		setActiveTaxonomy(model);
	}
	
	public Optional<Model> getActiveTaxonomy() {
		return activeTaxonomy == null ? Optional.empty() : Optional.of(activeTaxonomy);
	}

	public void setActiveTaxonomy(Model taxonomy) {
		activeTaxonomy = taxonomy;
		setChanged();
		notifyObservers(activeTaxonomy);
	}

	@Override
	public void notifyObservers(Object arg) {
		try {
			super.notifyObservers(arg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns a taxanomy file that is in the same project as the file of the resource.
	 * @param EMF resource
	 * @return Taxonomy file handle or null
	 */
	private IFile getTaxonomyFileInProject(Resource resource){
		IFile file = null;
		IFile ffr = getFileFromResource(resource);
		if(ffr != null) {
			IFile[] containers = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(ffr.getLocationURI());
			if(containers != null && containers.length > 0){
				IProject project = containers[0].getProject();
				if(project != null){
					try {
						//TODO: project.members() only returns immediate children, would need recursive function to walk through project.
						file = (IFile) Arrays.stream(project.members()).filter(x -> x.getType() == IResource.FILE && x.getFileExtension().equals("taxonomy")).reduce((a, b) -> b).orElse(null);
					} catch (CoreException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return file;
	}
	
	/**
	 * Gets a file for a EMF resource.
	 * 
	 * @param A EMF resource
	 * @return A file or null
	 */
	private IFile getFileFromResource(Resource resource)
	{
		if (resource != null)
		{
			URI uri = resource.getResourceSet().getURIConverter().normalize(resource.getURI());
			if ("platform".equals(uri.scheme()) && uri.segmentCount() > 1 && "resource".equals(uri.segment(0)))
			{
				StringBuffer platformResourcePath = new StringBuffer();
				for (int i = 1, size = uri.segmentCount(); i < size; ++i)
				{
					platformResourcePath.append('/');
					platformResourcePath.append(uri.segment(i));
				}
				return ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(platformResourcePath.toString()));
			}
		}
		return null;
	}
}
