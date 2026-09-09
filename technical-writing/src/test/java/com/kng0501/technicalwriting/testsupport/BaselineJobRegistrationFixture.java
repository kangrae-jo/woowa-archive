package com.kng0501.technicalwriting.testsupport;

import com.kng0501.dbpolling.persistence.ImageGenerationRequestRepository;
import com.kng0501.dbpolling.persistence.MonsterRepository;

public final class BaselineJobRegistrationFixture {

    private BaselineJobRegistrationFixture() {
    }

    public static long register(
            final MonsterRepository monsters,
            final ImageGenerationRequestRepository requests,
            final String prompt
    ) {
        final long monsterId = monsters.save(prompt);
        requests.enqueue(prompt);
        return monsterId;
    }
}
