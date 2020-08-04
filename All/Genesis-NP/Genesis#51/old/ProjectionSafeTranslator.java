package com.googlecode.objectify.impl.translate;

import com.google.appengine.api.datastore.RawValue;
import com.googlecode.objectify.impl.Path;

/**
 * <p>Watches out for RawValue and performs the necessary conversion if we get one. This will happen
 * during projection queries.</p>
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
abstract public class ProjectionSafeTranslator<P, D> extends NullSafeTranslator<P, D>
{
	/** The class in the datastore that we project to */
	private Class<? extends D> projectionClass;

	/** */
	public ProjectionSafeTranslator(Class<? extends D> projectionClass) {
		this.projectionClass = projectionClass;
	}

	@Override
	final protected P loadSafe(D value, LoadContext ctx, Path path) throws SkipException {
		// Projection queries produce RawValue because the index data is not self-describing.
		// Here we have the expected datastore type, so we can obtain it right away.
		if (value instanceof RawValue)
			value = (D)((RawValue)value).asType(projectionClass);

		return loadSafe2(value, ctx, path);
	}

	/**
	 * Decode from a property value as stored in the datastore to a type that will be stored in a pojo.
	 *
	 * @param value will not be null and will not be RawValue
	 * @return the format which should be stored in the pojo; a null means store a literal null!
	 * @throws com.googlecode.objectify.impl.translate.SkipException if this field subtree should be skipped
	 */
	abstract protected P loadSafe2(D value, LoadContext ctx, Path path) throws SkipException;
}
