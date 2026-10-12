package com.fleetflow.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FleetFlowApplication

fun main(args: Array<String>) {
    runApplication<FleetFlowApplication>(*args)
}
