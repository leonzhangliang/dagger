/*
 * Copyright (C) 2017 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Checks the assignability of one type to another, given a {@link ContributionType} context. This
 * is used by {@link BindsMethodValidator} to validate that the right-hand-side of a {@link
 * dagger.Binds} method is valid, as well as in {@link DelegateBindingExpression} when the
 * right-hand-side in generated code might be an erased type due to accessibility.
 */
final class BindsTypeChecker {
  private final Types types;
  private final Elements elements;

  @Inject
  BindsTypeChecker(Types types, Elements elements) {
    this.types = types;
    this.elements = elements;
  }

  /**
   * Checks the assignability of {@code rightHandSide} to {@code leftHandSide} given a {@link
   * ContributionType} context.
   */
  boolean isAssignable(
      TypeMirror rightHandSide, TypeMirror leftHandSide, ContributionType contributionType) {
    return types.isAssignable(rightHandSide, desiredAssignableType(leftHandSide, contributionType));
  }

  private TypeMirror desiredAssignableType(
      TypeMirror leftHandSide, ContributionType contributionType) {
    switch (contributionType) {
      case UNIQUE:
        return leftHandSide;
      case SET:
        DeclaredType parameterizedSetType = types.getDeclaredType(setElement(), leftHandSide);
        return methodParameterType(parameterizedSetType, "add");
      case SET_VALUES:
        return methodParameterType(MoreTypes.asDeclared(leftHandSide), "addAll");
      case MAP:
        DeclaredType parameterizedMapType =
            types.getDeclaredType(mapElement(), unboundedWildcard(), leftHandSide);
        return methodParameterTypes(parameterizedMapType, "put").get(1);
      default:
        throw new AssertionError("Unknown contribution type: " + contributionType);
    }
  }

  private ImmutableList<TypeMirror> methodParameterTypes(DeclaredType type, String methodName) {
    ImmutableList.Builder<ExecutableElement> methodsForName = ImmutableList.builder();
    for (ExecutableElement method :
        methodsIn(MoreElements.asType(type.asElement()).getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(methodName)) {
        methodsForName.add(method);
      }
    }
    ExecutableElement method = getOnlyElement(methodsForName.build());
    return ImmutableList.copyOf(
        MoreTypes.asExecutable(types.asMemberOf(type, method)).getParameterTypes());
  }

  private TypeMirror methodParameterType(DeclaredType type, String methodName) {
    return getOnlyElement(methodParameterTypes(type, methodName));
  }

  private TypeElement setElement() {
    return elements.getTypeElement(Set.class.getName());
  }

  private TypeElement mapElement() {
    return elements.getTypeElement(Map.class.getName());
  }

  private TypeMirror unboundedWildcard() {
    return types.getWildcardType(null, null);
  }
}
