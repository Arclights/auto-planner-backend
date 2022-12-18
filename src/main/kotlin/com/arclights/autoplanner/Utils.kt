package com.arclights.autoplanner

import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.models.Task
import kotlin.math.max

fun printResult(ganttResult: GanttResult) {
    val (tasks) = ganttResult

    val maxNameLength = tasks
        .map(Task::name)
        .maxOf(String::length)

    val maxTimeStringLength = tasks
        .map { getTimeString(it) }
        .maxOf(String::length)

    tasks.forEach { task -> printTask(task, maxNameLength, maxTimeStringLength) }
}

fun printTask(task: Task, maxNameLength: Int, maxTimeStringLength: Int) {
    val alignmentNamePaddingSize = maxNameLength - task.name.length
    val alignmentNamePadding = generatePadding(alignmentNamePaddingSize)

    val timeString = getTimeString(task)
    val alignmentTimeStringPaddingSize = maxTimeStringLength - timeString.length
    val alignmentTimeStringPadding = generatePadding(alignmentTimeStringPaddingSize)

    val startPadding = if (task.length == 1) {
        generatePadding(task.start)
    } else {
        generatePadding(max(0, task.start))
    }

    val taskBox = if (task.length == 1) {
        "|"
    } else {
        "[${generatePadding(max(0, task.length - 2))}]"
    }
    println("${task.name}$alignmentNamePadding$timeString$alignmentTimeStringPadding$startPadding$taskBox")
}

fun getTimeString(task: Task) = " ${task.length} ${task.start}-${task.start + task.length - 1} "

fun generatePadding(length: Int) = List(length) { _ -> ' ' }.joinToString("")