// Copyright 2011 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.secmgr.common;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An immutable accumulator that adds elements to the end of a "chain" and can
 * later convert that to a {@link List}.
 *
 * @param <E> The type of the elements stored in this chain.
 */
@Immutable
@ParametersAreNonnullByDefault
public abstract class Chain<E> implements Iterable<E> {

  @Nonnull private static final Chain<?> EMPTY_CHAIN = new EmptyChain<Object>();

  /**
   * Gets a new chain with no elements.
   */
  @CheckReturnValue
  @SuppressWarnings("unchecked")
  @Nonnull
  public static <F> Chain<F> empty() {
    return (Chain<F>) EMPTY_CHAIN;
  }

  /**
   * Gets a new chain with the given elements.
   *
   * @param elements The elements to make a chain from.
   * @return A chain of those elements.
   */
  @CheckReturnValue
  @Nonnull
  public static <F> Chain<F> copyOf(Iterable<F> elements) {
    Chain<F> chain = empty();
    for (F element : elements) {
      chain = chain.add(element);
    }
    return chain;
  }

  /**
   * Gets the number of elements in this chain.
   *
   * @return The number of elements.
   */
  @CheckReturnValue
  @Nonnegative
  public abstract int size();

  /**
   * Is this an empty chain?
   *
   * @return True if this chain has no elements.
   */
  @CheckReturnValue
  public abstract boolean isEmpty();

  /**
   * Gets the last element in this chain.  That is, for any chain {@code C} and
   * element {@code E}, {@code C.add(E).getLast() == E}.
   *
   * @return The last element in the chain.
   * @throws UnsupportedOperationException if this is an empty chain.
   */
  @CheckReturnValue
  @Nonnull
  public abstract E getLast();

  /**
   * Gets the rest of this chain.  That is, for any chain {@code C} and element
   * {@code E}, {@code C.add(E).getRest() == C}.
   *
   * @return The rest of this chain.
   * @throws UnsupportedOperationException if this is an empty chain.
   */
  @CheckReturnValue
  @Nonnull
  public abstract Chain<E> getRest();

  @Immutable
  private static final class EmptyChain<F> extends Chain<F> {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public F getLast() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Chain<F> getRest() {
      throw new UnsupportedOperationException();
    }
  }

  @Immutable
  @ParametersAreNonnullByDefault
  private static final class NonEmptyChain<F> extends Chain<F> {
    @Nonnegative final int numberOfElements;
    @Nullable final F element;
    @Nonnull final Chain<F> rest;

    NonEmptyChain(@Nullable F element, Chain<F> rest) {
      Preconditions.checkNotNull(rest);
      this.numberOfElements = rest.size() + 1;
      this.element = element;
      this.rest = rest;
    }

    @Override
    public int size() {
      return numberOfElements;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    @Nullable
    public F getLast() {
      return element;
    }

    @Override
    @Nonnull
    public Chain<F> getRest() {
      return rest;
    }
  }

  /**
   * Adds a new element to the chain.  Since this is an immutable structure, a
   * new chain is returned that contains the new element as well as all the
   * elements of this chain.  The new chain has a "last" of {@code element} and
   * a "rest" of {@code this}.
   *
   * @param element The element to be added.
   * @return The new chain with the added element.
   * @throws NullPointerException if element is null.
   */
  @CheckReturnValue
  @Nonnull
  public Chain<E> add(@Nullable E element) {
    return new NonEmptyChain<E>(element, this);
  }

  /**
   * Adds some new elements to the chain.
   *
   * @param elements The elements to be added.
   * @return The new chain with the added elements.
   */
  @CheckReturnValue
  @Nonnull
  public Chain<E> addAll(Iterable<E> elements) {
    Chain<E> chain = this;
    for (E element : elements) {
      chain = chain.add(element);
    }
    return chain;
  }

  /**
   * Gets an element from the chain.  A given index specifies the
   * element, where zero refers to the first element in the chain and
   * {@code size() - 1} refers to the last.
   *
   * @param index The index of the element to get.
   * @return The specified element.
   * @throws IllegalArgumentException if index is negative or too large.
   */
  @CheckReturnValue
  @Nonnull
  public E get(@Nonnegative int index) {
    Preconditions.checkArgument(index >= 0 && index < size());
    Chain<E> chain = this;
    for (int i = 0; i < size() - (index + 1); i += 1) {
      chain = chain.getRest();
    }
    return chain.getLast();
  }

  /**
   * Converts this chain to a set.
   *
   * @return The elements of this chain as a set.
   */
  @CheckReturnValue
  @Nonnull
  public Set<E> toSet() {
    return Sets.newHashSet(this);
  }

  /**
   * Converts this chain to a list, preserving the chain's order.
   *
   * @return The elements of this chain as a list.
   */
  @CheckReturnValue
  @Nonnull
  public List<E> toList() {
    LinkedList<E> list = Lists.newLinkedList();
    Chain<E> chain = this;
    for (int i = 0; i < size(); i += 1) {
      list.addFirst(chain.getLast());
      chain = chain.getRest();
    }
    return list;
  }

  /**
   * Given a chain that is an ancestor of this chain, returns the elements that
   * have been added to the ancestor to make this chain.
   *
   * <p>For any ancestor chain {@code C} it is always true that
   * {@code this.equals(C.addAll(this.toList(C)))}.
   *
   * @param ancestor The ancestor chain.
   * @return A list of the elements elements added .
   * @throws IllegalArgumentException if {@code chain} is not an ancestor.
   */
  @CheckReturnValue
  @Nonnull
  public List<E> toList(Chain<E> ancestor) {
    Preconditions.checkNotNull(ancestor);
    LinkedList<E> list = Lists.newLinkedList();
    Chain<E> chain = this;
    while (chain != ancestor) {
      if (chain.isEmpty()) {
        throw new IllegalArgumentException("Not an ancestor chain: " + ancestor);
      }
      list.addFirst(chain.getLast());
      chain = chain.getRest();
    }
    return list;
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) { return true; }
    if (!(object instanceof Chain<?>)) { return false; }
    Chain<?> other = (Chain<?>) object;
    if (size() != other.size()) { return false; }
    Chain<E> c1 = this;
    Chain<?> c2 = other;
    for (int i = 0; i < size(); i += 1) {
      if (!c1.getLast().equals(c2.getLast())) { return false; }
      c1 = c1.getRest();
      c2 = c2.getRest();
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(toList());
  }

  @Override
  public String toString() {
    return toList().toString();
  }

  /**
   * Gets an iterator for this chain.  The iterator starts with the last element
   * and moves towards the first.  If you want to iterate from first element to
   * last, use {@link #toList()}.
   */
  @Override
  public Iterator<E> iterator() {
    return new LocalIterator<E>(this);
  }

  @NotThreadSafe
  private static final class LocalIterator<F> implements Iterator<F> {
    Chain<F> chain;

    LocalIterator(Chain<F> chain) {
      this.chain = chain;
    }

    @Override
    public boolean hasNext() {
      return !chain.isEmpty();
    }

    @Override
    public F next() {
      if (chain.isEmpty()) { throw new NoSuchElementException(); }
      F value = chain.getLast();
      chain = chain.getRest();
      return value;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Converts an iterable of chains to an iterable of lists.  The resulting
   * lists have the same ordering as the input chains.
   *
   * @param chains The chains to convert.
   * @return The resulting lists.
   */
  @CheckReturnValue
  @Nonnull
  public static <T> Iterable<List<T>> toLists(Iterable<Chain<T>> chains) {
    return Iterables.transform(chains, Chain.<T>toListFunction());
  }

  /**
   * Gets a function that invokes the {@link #toList} method on its argument.
   */
  @CheckReturnValue
  @Nonnull
  public static <T> Function<Chain<T>, List<T>> toListFunction() {
    return new Function<Chain<T>, List<T>>() {
      @Override
      public List<T> apply(Chain<T> chain) {
        return chain.toList();
      }
    };
  }

  /**
   * Converts an iterable of chains to an iterable of sets.
   *
   * @param chains The chains to convert.
   * @return The resulting sets.
   */
  @CheckReturnValue
  @Nonnull
  public static <T> Iterable<Set<T>> toSets(Iterable<Chain<T>> chains) {
    return Iterables.transform(chains, Chain.<T>toSetFunction());
  }

  /**
   * Gets a function that invokes the {@link #toSet} method on its argument.
   */
  @CheckReturnValue
  @Nonnull
  public static <T> Function<Chain<T>, Set<T>> toSetFunction() {
    return new Function<Chain<T>, Set<T>>() {
      @Override
      public Set<T> apply(Chain<T> chain) {
        return chain.toSet();
      }
    };
  }

  /**
   * Extends an iterable of chains with a given element.  For each member of the
   * iterable, it creates a new member by adding the element to that member.
   *
   * @param chains The chains to be extended.
   * @param element The element to extend them with.
   * @return An iterable of the extended chains.
   */
  @CheckReturnValue
  @Nonnull
  public static <T> Iterable<Chain<T>> addToChains(Iterable<Chain<T>> chains, T element) {
    return Iterables.transform(chains, addFunction(element));
  }

  /**
   * Gets a function that adds a given element to a given chain.
   *
   * @param element The element to be added.
   * @return A function that extends its argument with {@code element}.
   */
  @CheckReturnValue
  @Nonnull
  public static <T> Function<Chain<T>, Chain<T>> addFunction(@Nullable final T element) {
    return new Function<Chain<T>, Chain<T>>() {
      @Override
      public Chain<T> apply(Chain<T> chain) {
        return chain.add(element);
      }
    };
  }
}
