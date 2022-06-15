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
public class NTriplesOntologyFormatFactory extends
        AbstractRioRDFOntologyFormatFactory implements
        RioRDFOntologyFormatFactory {

    public NTriplesOntologyFormatFactory() {
        super();
    }

    @Override
    public RioRDFOntologyFormat createFormat() {
        return new NTriplesOntologyFormat();
    }
}
