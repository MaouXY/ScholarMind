package com.maou.apptemplateapi.module.literature.service;

import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.LiteratureDetailResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LiteratureDetailService {

    private final LiteratureService literatureService;
    private final LiteratureFavoriteService literatureFavoriteService;
    private final ReadingHistoryService readingHistoryService;
    private final ReadingNoteService readingNoteService;

    public LiteratureDetailResponse detailView(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        LiteratureResponse literature = literatureService.detail(id);
        return new LiteratureDetailResponse(
                literature,
                literatureFavoriteService.isFavorited(id, currentUser.getId()),
                readingHistoryService.getByLiterature(id),
                readingNoteService.recentNotes(id, currentUser.getId(), 5)
        );
    }
}
