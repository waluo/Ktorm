/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.liuwj.ktorm.entity

import me.liuwj.ktorm.schema.Column
import me.liuwj.ktorm.schema.NestedBinding
import me.liuwj.ktorm.schema.ReferenceBinding
import me.liuwj.ktorm.schema.Table
import java.util.*
import kotlin.reflect.jvm.jvmErasure

internal fun EntityImplementation.getPrimaryKeyValue(fromTable: Table<*>): Any? {
    val primaryKey = fromTable.primaryKey ?: error("Table ${fromTable.tableName} doesn't have a primary key.")
    return getColumnValue(primaryKey)
}

internal fun EntityImplementation.getColumnValue(column: Column<*>): Any? {
    val binding = column.binding ?: error("Column $column has no bindings to any entity field.")

    when (binding) {
        is ReferenceBinding -> {
            val child = this.getProperty(binding.onProperty.name) as Entity<*>?
            return child?.implementation?.getPrimaryKeyValue(binding.referenceTable as Table<*>)
        }
        is NestedBinding -> {
            var curr: EntityImplementation? = this
            for ((i, prop) in binding.properties.withIndex()) {
                if (i != binding.properties.lastIndex) {
                    val child = curr?.getProperty(prop.name) as Entity<*>?
                    curr = child?.implementation
                }
            }
            return curr?.getProperty(binding.properties.last().name)
        }
    }
}

internal fun EntityImplementation.setPrimaryKeyValue(fromTable: Table<*>, value: Any?, forceSet: Boolean = false) {
    val primaryKey = fromTable.primaryKey ?: error("Table ${fromTable.tableName} doesn't have a primary key.")
    setColumnValue(primaryKey, value, forceSet)
}

internal fun EntityImplementation.setColumnValue(column: Column<*>, value: Any?, forceSet: Boolean = false) {
    val binding = column.binding ?: error("Column $column has no bindings to any entity field.")

    when (binding) {
        is ReferenceBinding -> {
            var child = this.getProperty(binding.onProperty.name) as Entity<*>?
            if (child == null) {
                child = Entity.create(
                    entityClass = binding.onProperty.returnType.jvmErasure,
                    fromTable = binding.referenceTable as Table<*>
                )
                this.setProperty(binding.onProperty.name, child, forceSet)
            }

            child.implementation.setPrimaryKeyValue(binding.referenceTable as Table<*>, value, forceSet)
        }
        is NestedBinding -> {
            var curr: EntityImplementation = this
            for ((i, prop) in binding.properties.withIndex()) {
                if (i != binding.properties.lastIndex) {
                    var child = curr.getProperty(prop.name) as Entity<*>?
                    if (child == null) {
                        child = Entity.create(prop.returnType.jvmErasure, parent = curr)
                        curr.setProperty(prop.name, child, forceSet)
                    }

                    curr = child.implementation
                }
            }

            curr.setProperty(binding.properties.last().name, value, forceSet)
        }
    }
}

internal fun EntityImplementation.isPrimaryKey(name: String): Boolean {
    val binding = this.fromTable?.primaryKey?.binding

    when (binding) {
        null -> return false
        is ReferenceBinding -> {
            return binding.onProperty.name == name
        }
        is NestedBinding -> {
            val namesPath = LinkedList<Set<String>>()
            namesPath.addFirst(setOf(name))

            var curr: EntityImplementation = this
            while (true) {
                val parent = curr.parent ?: break
                val children = parent.values.filterValues { it == curr }

                if (children.isEmpty()) {
                    break
                } else {
                    namesPath.addFirst(children.keys)
                    curr = parent
                }
            }

            for ((i, possibleFields) in namesPath.withIndex()) {
                if (binding.properties[i].name !in possibleFields) {
                    return false
                }
            }

            return true
        }
    }
}

internal val Entity<*>.implementation: EntityImplementation get() {
    return java.lang.reflect.Proxy.getInvocationHandler(this) as EntityImplementation
}
