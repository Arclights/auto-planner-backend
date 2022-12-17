package com.arclights.autoplanner

import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.Task
import kotlin.math.max

fun printResult(ganttResult: GanttResult) {
    val maxNameLength = ganttResult.tasks
        .map(Task::name)
        .maxOf(String::length)

    ganttResult.tasks.forEach { task -> printTask(task, maxNameLength) }
}

fun printTask(task: Task, maxNameLength: Int) {
    val alignmentPaddingSize = maxNameLength - task.name.length
    val alignmentPadding = List(alignmentPaddingSize) { _ -> ' ' }.joinToString("")

    val startPadding = if (task.length == 1) {
        List(task.start) { _ -> ' ' }.joinToString("")
    } else {
        List(max(0, task.start - 1)) { _ -> ' ' }.joinToString("")
    }

    val taskBox = if (task.length == 1) {
        "|"
    } else {
        List(max(0, task.length - 2)) { _ -> ' ' }.joinToString("", "[", "]")
    }
    println("${task.name}$alignmentPadding$startPadding$taskBox")
}