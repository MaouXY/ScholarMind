package com.maou.apptemplateapi.module.admin.dto;

public record AdminTrendPointResponse(
        String date,
        Long newUsers,
        Long newLiteratures,
        Long readingEvents,
        Long qaEvents,
        Long aiTasks
) {
}
