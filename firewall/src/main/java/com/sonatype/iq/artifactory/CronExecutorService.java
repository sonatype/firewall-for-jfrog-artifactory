/*
 * Copyright 2019, Sonatype, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sonatype.iq.artifactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.slf4j.Logger;

import static com.cronutils.model.CronType.QUARTZ;

public class CronExecutorService
{
  private ScheduledThreadPoolExecutor delegate;

  private String fallbackCronExpression;

  private Logger log;

  public CronExecutorService(final int corePoolSize) {
    delegate = new ScheduledThreadPoolExecutor(corePoolSize);
  }

  public void scheduleWithCronExpression(final Runnable command, final String cronExpression, final Logger log) {
    scheduleWithCronExpression(command, cronExpression, null, log);
  }

  public void scheduleWithCronExpression(
      final Runnable command,
      final String cronExpression,
      final String fallbackCronExpression,
      final Logger log) {
    this.fallbackCronExpression = fallbackCronExpression;
    this.log = log;
    scheduleWithCronExpression(command, cronExpression, ZonedDateTime.now());
  }

  private void scheduleWithCronExpression(
      final Runnable command,
      final String cronExpression,
      final ZonedDateTime lastExecution)
  {
    CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));
    Cron cron = parser.parse(cronExpression).validate();

    ExecutionTime executionTime = ExecutionTime.forCron(cron);
    Optional<Duration> timeToNextExecution = executionTime.timeToNextExecution(lastExecution);
    Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(lastExecution);

    if (timeToNextExecution.isPresent() && nextExecution.isPresent()) {
      delegate.schedule(command, timeToNextExecution.get().getSeconds(), TimeUnit.SECONDS);
      delegate.schedule(() -> scheduleWithCronExpression(command, cronExpression, nextExecution.get()),
          timeToNextExecution.get().getSeconds(), TimeUnit.SECONDS);
    }
    else {
      if (fallbackCronExpression == null || fallbackCronExpression.equals(cronExpression)) {
        throw new IllegalArgumentException("Cannot compute next scheduled execution. Cron expression: " + cronExpression);
      }

      log.error("Cannot compute next scheduled execution. Using default. Failed cron expression: " + cronExpression);
      scheduleWithCronExpression(command, fallbackCronExpression, lastExecution);
    }
  }
}
