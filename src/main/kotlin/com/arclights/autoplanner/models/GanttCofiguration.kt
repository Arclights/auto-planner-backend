package com.arclights.autoplanner.models

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class GanttConfig(
    val resourceAllocations: List<ResourceAllocationConfig>,
    val tasks: List<TaskConfig>
)

@Serdeable
data class ResourceAllocationConfig(val timeUnitFrom: Int, val timeUnitUntil: Int, val nbrOfResources: Int)

@Serdeable
data class TaskConfig(
    val id: String,
    val name: String,
    val length: Int,
    val timeScalar: TimeScalar?,
    val relationConstraints: List<RelationConstraint> = listOf()
)

@Serdeable
data class RelationConstraint(val type: RelationConstraintType, val taskIdRelationTo: String)

@Serdeable
enum class RelationConstraintType {
    BEFORE, AFTER
}

@Serdeable
enum class TimeScalar {
    LINEAR, LOGARITHMIC
}