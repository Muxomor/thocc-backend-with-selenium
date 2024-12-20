package com.thocc.di

import com.thocc.services.GeekhackCheckerService
import com.thocc.services.ZFrontierCheckerService
import io.ktor.server.application.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private const val JOB_INTERVAL_5_M = 5 * 60 * 1000L
private const val JOB_INTERVAL_2_H = 2 * 60 * 60 * 1000L

fun Application.configureBackgroundJobs() {

    //val geekhackCheckerService: GeekhackCheckerService by inject<GeekhackCheckerService>()
    val zFrontierCheckerService: ZFrontierCheckerService by inject<ZFrontierCheckerService>()
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    //launch {
    //    logger.info("geekhack checker background job started!")
    //    while (isActive) {
    //        try {
    //            //TODO("Убрать когда закончу")
    //            geekhackCheckerService.checkGeekhackFeeds()
    //        } catch (e: Exception) {
    //            logger.error("Some error appeared in Background Job: ${e.message}")
    //        }
    //        delay(JOB_INTERVAL_5_M)
    //    }
    //}
    launch {
        logger.info("zfrontier checker background job started!")
        while (isActive) {
            try {
                zFrontierCheckerService.startChecker()
            } catch (e: Exception) {
                logger.error("some error appeared in background job:${e.message}")
            }
        }
    }
}