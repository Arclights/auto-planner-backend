package com.arclights.autoplanner.models

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class GanttResult(val tasks: List<Task>)

@Serdeable
data class Task(
//    val id:String,
    val name: String,
    val start: Int,
    val length: Int
)