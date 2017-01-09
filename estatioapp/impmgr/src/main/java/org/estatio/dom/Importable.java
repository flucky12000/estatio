/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.estatio.dom;

import java.util.List;

public interface Importable {

    /**
     * @param previousRow, if any.
     * @return created objects (so that the fixture framework can make available to calling test or in the UI)
     */
    List<Object> importData(Object previousRow);

    /**
     * Defines the order in which all {@link Importable}s are loaded.
     *
     * <p>
     *     NB: this is to replace the ImportOrder that has a hard-coded list of classes (WIP).
     * </p>
     */
    List<Class> importAfter();

}