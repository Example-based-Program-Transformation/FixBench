package com.googlecode.objectify.impl;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.ReadPolicy;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.TxnType;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Deleter;
import com.googlecode.objectify.cmd.Loader;
import com.googlecode.objectify.cmd.Saver;
import com.googlecode.objectify.impl.translate.CreateContext;
import com.googlecode.objectify.impl.translate.SaveContext;
import com.googlecode.objectify.impl.translate.Translator;

/**
 * <p>Implementation of the Objectify interface. This is also suitable for subclassing; you
 * can return your own subclass by overriding ObjectifyFactory.begin().</p>
 *
 * <p>Note we *always* use the AsyncDatastoreService
 * methods that use transactions to avoid the confusion of implicit transactions.</p>
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
public class ObjectifyImpl<O extends Objectify> implements Objectify, Cloneable
{
	/** The factory that produced us */
	protected ObjectifyFactory factory;

	/** Our options */
	protected boolean cache = true;
	protected Consistency consistency = Consistency.STRONG;
	protected Double deadline;

	/** */
	protected Transactor<O> transactor = new TransactorNo<O>();

	/**
	 */
	public ObjectifyImpl(ObjectifyFactory fact) {
		this.factory = fact;
	}

	/** Copy constructor */
	public ObjectifyImpl(ObjectifyImpl<O> other) {
		this.factory = other.factory;
		this.cache = other.cache;
		this.consistency = other.consistency;
		this.deadline = other.deadline;
		this.transactor = other.transactor;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#getFactory()
	 */
	public ObjectifyFactory factory() {
		return this.factory;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#getFactory()
	 */
	@Deprecated
	public ObjectifyFactory getFactory() {
		return factory();
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#find()
	 */
	@Override
	public Loader load() {
		return new LoaderImpl<Loader>(this);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#put()
	 */
	@Override
	public Saver save() {
		return new SaverImpl(this);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#delete()
	 */
	@Override
	public Deleter delete() {
		return new DeleterImpl(this);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#consistency(com.google.appengine.api.datastore.ReadPolicy.Consistency)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public O consistency(Consistency value) {
		if (value == null)
			throw new IllegalArgumentException("Consistency cannot be null");

		ObjectifyImpl<O> clone = this.clone();
		clone.consistency = value;
		return (O)clone;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#deadline(java.lang.Double)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public O deadline(Double value) {
		ObjectifyImpl<O> clone = this.clone();
		clone.deadline = value;
		return (O)clone;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#cache(boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public O cache(boolean value) {
		ObjectifyImpl<O> clone = this.clone();
		clone.cache = value;
		return (O)clone;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#transactionless()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public O transactionless() {
		return (O)transactor.transactionless(this);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@SuppressWarnings("unchecked")
	protected ObjectifyImpl<O> clone() {
		try {
			return (ObjectifyImpl<O>)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e); // impossible
		}
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#getTxn()
	 */
	public TransactionImpl getTransaction() {
		return transactor.getTransaction();
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#execute(com.googlecode.objectify.TxnType, com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R execute(TxnType txnType, Work<R> work) {
		return transactor.execute(null, txnType, work);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#transact(com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R transact(Work<R> work) {
		return transactor.transact(this, work);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#transact(com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R transactNew(Work<R> work) {
		return this.transactNew(Integer.MAX_VALUE, work);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#transactNew(com.googlecode.objectify.Work)
	 */
	@Override
	public <R> R transactNew(int limitTries, Work<R> work) {
		return transactor.transactNew(this, limitTries, work);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#clear()
	 */
	@Override
	public void clear() {
		transactor.getSession().clear();
	}

	/**
	 * Make a datastore service config that corresponds to our options.
	 */
	protected DatastoreServiceConfig createDatastoreServiceConfig() {
		DatastoreServiceConfig cfg = DatastoreServiceConfig.Builder.withReadPolicy(new ReadPolicy(consistency));

		if (deadline != null)
			cfg.deadline(deadline);

		return cfg;
	}

	/**
	 * Make a datastore service config that corresponds to our options.
	 */
	protected AsyncDatastoreService createAsyncDatastoreService() {
		return factory.createAsyncDatastoreService(this.createDatastoreServiceConfig(), cache);
	}

	/**
	 * Use this once for one operation and then throw it away
	 * @return a fresh engine that handles fundamental datastore operations for saving and deleting
	 */
	protected WriteEngine createWriteEngine() {
		return new WriteEngine(this, createAsyncDatastoreService(), transactor.getSession());
	}

	/**
	 * <p>Translates the value of a filter clause into something the datastore understands.  Key<?> goes to native Key,
	 * entities go to native Key, java.sql.Date goes to java.util.Date, etc.  It uses the same translation system
	 * that is used for standard entity fields, but does no checking to see if the value is appropriate for the field.</p>
	 *
	 * <p>Unrecognized types are returned as-is.</p>
	 *
	 * <p>A future version of this method might check for type validity.</p>
	 *
	 * @return whatever can be put into a filter clause.
	 */
	protected Object makeFilterable(Object value) {
		if (value == null)
			return null;

		// This is really quite a dilemma.  We need to convert that value into something we can filter by, but we don't
		// really have a lot of information about it.  We could use type information from the matched field, but there's
		// no guarantee that there is a field to check - it could be a typeless query or a query on an old property value.
		// The only real solution is to create a (non root!) translator on the fly.  Even that is not straightforward,
		// because erasure wipes out any component type information in a collection.
		//
		// The answer:  Check for collections explicitly.  Create a separate translator for every item in the collection;
		// after all, it could be a heterogeneous list.  This is not especially efficient but GAE only allows a handful of
		// items in a IN operation and at any rate processing will still be negligible compared to the cost of a query.

		// If this is an array, make life easier by turning it into a list first.  Because of primitive
		// mismatching we can't trust Arrays.asList().
		if (value.getClass().isArray()) {
			int len = Array.getLength(value);
			List<Object> asList = new ArrayList<Object>(len);
			for (int i=0; i<len; i++)
				asList.add(Array.get(value, i));

			value = asList;
		}

		if (value instanceof Iterable) {
			List<Object> result = new ArrayList<Object>(50);	// hard limit is 30, but wth
			for (Object obj: (Iterable<?>)value)
				result.add(makeFilterable(obj));

			return result;
		} else {
			// Special case entity pojos that become keys
			KeyMetadata<Object> meta = Keys.getMetadata(value);
			if (meta != null) {
				return meta.getRawKey(value);
			} else {
				// Run it through the translator
				Translator<Object> translator = factory().getTranslators().create(Path.root(), NullProperty.INSTANCE, value.getClass(), new CreateContext(factory()));
				Node node = translator.save(value, Path.root(), false, new SaveContext(this));
				return getFilterableValue(node, value);
			}
		}
	}

	/** Extracts a filterable value from the node, or throws an illegalstate exception */
	private Object getFilterableValue(Node node, Object originalValue) {
		if (!node.hasPropertyValue())
			throw new IllegalStateException("Don't know how to filter by '" + originalValue + "'");

		return node.getPropertyValue();
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#toEntity(java.lang.Object)
	 */
	@Override
	public Entity toEntity(Object pojo) {
		return save().toEntity(pojo);
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#toPojo(com.google.appengine.api.datastore.Entity)
	 */
	@Override
	public <T> T toPojo(Entity entity) {
		return load().fromEntity(entity);
	}

	/** */
	protected Session getSession() {
		return this.transactor.getSession();
	}

	/** @return true if cache is enabled */
	public boolean getCache() {
		return cache;
	}

	/* (non-Javadoc)
	 * @see com.googlecode.objectify.Objectify#isLoaded(com.googlecode.objectify.Key)
	 */
	@Override
	public boolean isLoaded(Key<?> key) {
		return transactor.getSession().contains(key);
	}

}