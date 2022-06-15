package net.trackmate.model.abstractmodel;

import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import net.trackmate.util.mempool.MappedElement;
import net.trackmate.util.mempool.MemPool;

public class PoolObjectList< O extends PoolObject< T >, T extends MappedElement > implements PoolObjectCollection< O, T >, List< O >
{
	private final TIntList indices;

	private final Pool< O, T > pool;

	public PoolObjectList( final Pool< O, T > pool, final int initialCapacity )
	{
		this.pool = pool;
		indices = new TIntArrayList( initialCapacity );
	}

	protected PoolObjectList( final PoolObjectList< O, T > list, final TIntList indexSubList )
	{
		pool = list.pool;
		indices = indexSubList;
	}

	@Override
	public TIntCollection getIndexCollection()
	{
		return indices;
	}

	@Override
	public boolean add( final O obj )
	{
		return indices.add( obj.getInternalPoolIndex() );
	}

	@Override
	public void add( final int index, final O obj )
	{
		indices.insert( index, obj.getInternalPoolIndex() );
	}

	@Override
	public boolean addAll( final Collection< ? extends O > objs )
	{
		if ( objs instanceof PoolObjectCollection )
			return indices.addAll( ( ( PoolObjectCollection< ?, ? > ) objs ).getIndexCollection() );
		else
		{
			for ( final O obj : objs )
				indices.add( obj.getInternalPoolIndex() );
			return !objs.isEmpty();
		}
	}

	@Override
	public boolean addAll( final int index, final Collection< ? extends O > objs )
	{
		if ( objs instanceof PoolObjectCollection )
		{
			final TIntCollection objIndices = ( ( PoolObjectCollection< ?, ? > ) objs ).getIndexCollection();
			indices.insert( index, objIndices.toArray() );
		}
		else
		{
			final int[] indicesToInsert = new int[ objs.size() ];
			int i = 0;
			for ( final O obj : objs )
				indicesToInsert[ i++ ] = obj.getInternalPoolIndex();
			indices.insert( index, indicesToInsert );
		}
		return !objs.isEmpty();
	}

	@Override
	public void clear()
	{
		indices.clear();
	}

	@Override
	public boolean contains( final Object obj )
	{
		return ( obj instanceof PoolObject )
				? indices.contains( ( (net.trackmate.model.abstractmodel.PoolObject< ? > ) obj ).getInternalPoolIndex() )
				: false;
	}

	@Override
	public boolean containsAll( final Collection< ? > objs )
	{
		if ( objs instanceof PoolObjectCollection )
			return indices.containsAll( ( ( PoolObjectCollection< ?, ? > ) objs ).getIndexCollection() );
		else
		{
			for ( final Object obj : objs )
				if ( !contains( obj ) )
					return false;
			return true;
		}
	}

	public O get( final int index, final O obj )
	{
		obj.updateAccess( pool.getMemPool(), index );
		return obj;
	}

	@Override
	public O get( final int index )
	{
		return get( index, pool.createEmptyRef() );
	}

	@Override
	public int indexOf( final Object obj )
	{
		return ( obj instanceof PoolObject )
				? indices.indexOf( ( ( PoolObject< ? > ) obj ).getInternalPoolIndex() )
				: -1;
	}

	@Override
	public boolean isEmpty()
	{
		return indices.isEmpty();
	}

	@Override
	public Iterator< O > iterator()
	{
		return new Iterator< O >()
		{
			final MemPool< T > memPool = pool.getMemPool();

			final TIntIterator ii = indices.iterator();

			final O obj = pool.createEmptyRef();

			@Override
			public boolean hasNext()
			{
				return ii.hasNext();
			}

			@Override
			public O next()
			{
				final int index = ii.next();
				obj.updateAccess( memPool, index );
				return obj;
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public int lastIndexOf( final Object obj )
	{
		return ( obj instanceof PoolObject )
				? indices.lastIndexOf( ( ( PoolObject< ? > ) obj ).getInternalPoolIndex() )
				: -1;
	}

	// TODO: modCount is not updated currently.
	private final int modCount = 0;

	/**
	 * Shamelessly stolen from java.util.AbstractList
	 */
	private class ListItr implements ListIterator< O >
	{
		final MemPool< T > memPool = pool.getMemPool();

		final TIntIterator ii = indices.iterator();

		final O obj = pool.createEmptyRef();

		/**
		 * Index of element to be returned by subsequent call to next.
		 */
		int cursor;

		/**
		 * Index of element returned by most recent call to next or previous.
		 * Reset to -1 if this element is deleted by a call to remove.
		 */
		int lastRet = -1;

		/**
		 * The modCount value that the iterator believes that the backing List
		 * should have. If this expectation is violated, the iterator has
		 * detected concurrent modification.
		 */
		int expectedModCount = modCount;

		ListItr( final int index )
		{
			cursor = index;
		}

		@Override
		public boolean hasNext()
		{
			return cursor != size();
		}

		@Override
		public O next()
		{
			checkForComodification();
			try
			{
				final int i = cursor;
				final O next = get( i, obj );
				lastRet = i;
				cursor = i + 1;
				return next;
			}
			catch ( final IndexOutOfBoundsException e )
			{
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public void remove()
		{
			if ( lastRet < 0 )
				throw new IllegalStateException();
			checkForComodification();

			try
			{
				PoolObjectList.this.remove( lastRet );
				if ( lastRet < cursor )
					cursor--;
				lastRet = -1;
				expectedModCount = modCount;
			}
			catch ( final IndexOutOfBoundsException e )
			{
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public boolean hasPrevious()
		{
			return cursor != 0;
		}

		@Override
		public O previous()
		{
			checkForComodification();
			try
			{
				final int i = cursor - 1;
				final O previous = get( i, obj );
				lastRet = cursor = i;
				return previous;
			}
			catch ( final IndexOutOfBoundsException e )
			{
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public int nextIndex()
		{
			return cursor;
		}

		@Override
		public int previousIndex()
		{
			return cursor - 1;
		}

		@Override
		public void set( final O o )
		{
			if ( lastRet < 0 )
				throw new IllegalStateException();
			checkForComodification();

			try
			{
				PoolObjectList.this.set( lastRet, o );
				expectedModCount = modCount;
			}
			catch ( final IndexOutOfBoundsException ex )
			{
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add( final O o )
		{
			checkForComodification();

			try
			{
				final int i = cursor;
				PoolObjectList.this.add( i, o );
				lastRet = -1;
				cursor = i + 1;
				expectedModCount = modCount;
			}
			catch ( final IndexOutOfBoundsException ex )
			{
				throw new ConcurrentModificationException();
			}
		}

		final void checkForComodification()
		{
			if ( modCount != expectedModCount )
				throw new ConcurrentModificationException();
		}
	}

    @Override
	public ListIterator< O > listIterator()
	{
		return new ListItr( 0 );
	}

	@Override
	public ListIterator< O > listIterator( final int index )
	{
		return new ListItr( index );
	}

	@Override
	public boolean remove( final Object obj )
	{
		return ( obj instanceof PoolObject )
				? indices.remove( ( ( PoolObject< ? > ) obj ).getInternalPoolIndex() )
				: false;
	}

	public O remove( final int index, final O obj )
	{
		obj.updateAccess( pool.getMemPool(), indices.removeAt( index ) );
		return obj;
	}

	@Override
	public O remove( final int index )
	{
		return remove( index, pool.createEmptyRef() );
	}

	@Override
	public boolean removeAll( final Collection< ? > objs )
	{
		if ( objs instanceof PoolObjectCollection )
			return indices.removeAll( ( ( PoolObjectCollection< ?, ? > ) objs ).getIndexCollection() );
		else
		{
			boolean changed = false;
			for ( final Object obj : objs )
				if ( remove( obj ) )
					changed = true;
			return changed;
		}
	}

	@Override
	public boolean retainAll( final Collection< ? > objs )
	{
		if ( objs instanceof PoolObjectCollection )
			return indices.retainAll( ( ( PoolObjectCollection< ?, ? > ) objs ).getIndexCollection() );
		else
		{
			// TODO
			throw new UnsupportedOperationException( "not yet implemented" );
		}
	}

	public O set( final int index, final O obj, final O replacedObj )
	{
		replacedObj.updateAccess(
				pool.getMemPool(),
				indices.set( index, obj.getInternalPoolIndex() ) );
		return replacedObj;
	}

	@Override
	public O set( final int index, final O obj )
	{
		return set( index, obj, pool.createEmptyRef() );
	}

	@Override
	public int size()
	{
		return indices.size();
	}

	@Override
	public List< O > subList( final int fromIndex, final int toIndex )
	{
		return new PoolObjectList< O, T >( this, indices.subList( fromIndex, fromIndex ) );
	}

	@Override
	public Object[] toArray()
	{
		// TODO
		throw new UnsupportedOperationException( "not yet implemented" );
	}

	@Override
	public < T > T[] toArray( final T[] a )
	{
		// TODO
		throw new UnsupportedOperationException( "not yet implemented" );
	}
}
