/**
 * 
 */
package org.semanticweb.owlapi.formats;

import org.kohsuke.MetaInfServices;
import org.semanticweb.owlapi.model.OWLOntologyFormatFactory;

/**
 * @author Peter Ansell p_ansell@yahoo.com
 */
@MetaInfServices(OWLOntologyFormatFactory.class)
public class RdfJsonOntologyFormatFactory extends
        AbstractRioRDFOntologyFormatFactory implements OWLOntologyFormatFactory {

    private static final long serialVersionUID = 40000L;

    @Override
    public RioRDFOntologyFormat createFormat() {
        return new RdfJsonOntologyFormat();
    }
}
