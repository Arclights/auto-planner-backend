package com.arclights.autoplanner.controllers

import com.arclights.autoplanner.models.GanttConfig
import com.arclights.autoplanner.models.GanttResult
import com.arclights.autoplanner.printResult
import com.arclights.autoplanner.services.GanttSolverService
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import javax.validation.Valid

@Controller("/gantt")
open class GanttController(private val ganttSolverService: GanttSolverService) {

    @Post("/calculate/{id}")
    open fun calculate(@PathVariable id: String, @Body @Valid ganttConfig: GanttConfig): GanttResult {
        val res = ganttSolverService.calculate(ganttConfig)
        println(res)
        printResult(res)
        return res
    }
}