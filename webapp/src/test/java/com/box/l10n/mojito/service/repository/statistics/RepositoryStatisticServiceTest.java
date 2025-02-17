package com.box.l10n.mojito.service.repository.statistics;

import com.box.l10n.mojito.entity.Repository;
import com.box.l10n.mojito.entity.RepositoryLocale;
import com.box.l10n.mojito.entity.RepositoryLocaleStatistic;
import com.box.l10n.mojito.entity.RepositoryStatistic;
import com.box.l10n.mojito.entity.TMTextUnitVariant;
import com.box.l10n.mojito.service.asset.VirtualAsset;
import com.box.l10n.mojito.service.asset.VirtualAssetService;
import com.box.l10n.mojito.service.asset.VirtualAssetTextUnit;
import com.box.l10n.mojito.service.assetExtraction.ServiceTestBase;
import com.box.l10n.mojito.service.pollableTask.PollableFuture;
import com.box.l10n.mojito.service.repository.RepositoryRepository;
import com.box.l10n.mojito.service.repository.RepositoryService;
import com.box.l10n.mojito.service.tm.TMService;
import com.box.l10n.mojito.service.tm.TMTestData;
import com.box.l10n.mojito.test.TestIdWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author jaurambault
 */
public class RepositoryStatisticServiceTest extends ServiceTestBase {

    /**
     * logger
     */
    static Logger logger = getLogger(RepositoryStatisticServiceTest.class);

    @Autowired
    RepositoryStatisticService repositoryStatisticService;

    @Autowired
    RepositoryRepository repositoryRepository;

    @Autowired
    TMService tmService;

    @Autowired
    RepositoryStatisticRepository repositoryStatisticRepository;

    @Autowired
    RepositoryLocaleStatisticRepository repositoryLocaleStatisticRepository;

    @Autowired
    VirtualAssetService virtualAssetService;

    @Autowired
    RepositoryService repositoryService;

    @Rule
    public TestIdWatcher testIdWatcher = new TestIdWatcher();

    @Test
    public void testUpdateStatistics() throws Exception {

        TMTestData tmTestData = new TMTestData(testIdWatcher);

        logger.debug("First run: compute and save statistics");
        repositoryStatisticService.updateStatistics(tmTestData.repository.getId());

        Repository repository = repositoryRepository.findById(tmTestData.repository.getId()).orElse(null);
        RepositoryStatistic repositoryStatistic = repositoryStatisticRepository.findById(repository.getRepositoryStatistic().getId()).orElse(null);
        Map<String, RepositoryLocaleStatistic> repositoryLocaleStatistics = new HashMap<>();
        for (RepositoryLocaleStatistic repositoryLocaleStatistic : repositoryLocaleStatisticRepository.findByRepositoryStatisticId(repositoryStatistic.getId())) {
            repositoryLocaleStatistics.put(repositoryLocaleStatistic.getLocale().getBcp47Tag(), repositoryLocaleStatistic);
        }

        checkTextUnitCounts(repositoryStatistic);

        checkRepositoryLocaleStatistic(repositoryLocaleStatistics.get("fr-FR"), "fr-FR", 1, 8, 1, 8, 0, 0, 0, 0, 1, 1);
        checkRepositoryLocaleStatistic(repositoryLocaleStatistics.get("fr-CA"), "fr-CA", 1, 1, 1, 1, 0, 0, 0, 0, 1, 8);
        checkRepositoryLocaleStatistic(repositoryLocaleStatistics.get("ja-JP"), "ja-JP", 0, 0, 0, 0, 0, 0, 0, 0, 2, 9);
        checkRepositoryLocaleStatistic(repositoryLocaleStatistics.get("ko-KR"), "ko-KR", 1, 8, 1, 8, 0, 0, 0, 0, 1, 1);
    }

    @Test
    public void testComputeBaseStatistics() throws Exception {

        TMTestData tmTestData = new TMTestData(testIdWatcher);

        RepositoryStatistic repositoryStatistic = repositoryStatisticService.computeBaseStatistics(tmTestData.repository.getId());

        checkTextUnitCounts(repositoryStatistic);
    }

    @Test
    public void testComputeBaseStatisticsDoNotTranslate() throws Exception {

        Repository repository = repositoryService.createRepository(testIdWatcher.getEntityName("repository"));
        RepositoryLocale repoLocaleFrFR = repositoryService.addRepositoryLocale(repository, "fr-FR");
        RepositoryLocale repoLocaleJaJP = repositoryService.addRepositoryLocale(repository, "ja-JP");


        VirtualAsset virtualAsset = new VirtualAsset();
        virtualAsset.setPath("forStats");
        virtualAsset.setRepositoryId(repository.getId());
        virtualAssetService.createOrUpdateVirtualAsset(virtualAsset);

        VirtualAssetTextUnit virtualAssetTextUnit = new VirtualAssetTextUnit();
        virtualAssetTextUnit.setName("test1 name");
        virtualAssetTextUnit.setContent("test1 content");
        virtualAssetTextUnit.setDoNotTranslate(Boolean.FALSE);

        VirtualAssetTextUnit virtualAssetTextUnit2 = new VirtualAssetTextUnit();
        virtualAssetTextUnit2.setName("test2 name");
        virtualAssetTextUnit2.setContent("test2 content");
        virtualAssetTextUnit2.setDoNotTranslate(Boolean.TRUE);

        VirtualAssetTextUnit virtualAssetTextUnit3 = new VirtualAssetTextUnit();
        virtualAssetTextUnit3.setName("test3 name");
        virtualAssetTextUnit3.setContent("test3 content");
        virtualAssetTextUnit3.setDoNotTranslate(Boolean.FALSE);

        virtualAssetService.addTextUnits(virtualAsset.getId(), Arrays.asList(virtualAssetTextUnit, virtualAssetTextUnit2, virtualAssetTextUnit3)).get();
        PollableFuture<Void> replaceTextUnits = virtualAssetService.replaceTextUnits(virtualAsset.getId(), Arrays.asList(virtualAssetTextUnit, virtualAssetTextUnit2));
        replaceTextUnits.get();

        RepositoryStatistic repositoryStatistic = repositoryStatisticService.computeBaseStatistics(repository.getId());

        assertEquals(2L, (long) repositoryStatistic.getUsedTextUnitCount());
        assertEquals(4L, (long) repositoryStatistic.getUsedTextUnitWordCount());
        assertEquals(1L, (long) repositoryStatistic.getUnusedTextUnitCount());
        assertEquals(2L, (long) repositoryStatistic.getUnusedTextUnitWordCount());

        RepositoryLocaleStatistic repositoryLocaleStatisticFrFR = repositoryStatisticService.computeLocaleStatistics(repoLocaleFrFR);
        RepositoryLocaleStatistic repositoryLocaleStatisticJaJP = repositoryStatisticService.computeLocaleStatistics(repoLocaleJaJP);

        checkRepositoryLocaleStatistic(repositoryLocaleStatisticFrFR, "fr-FR", 0, 0, 0, 0, 0, 0, 0, 0, 1, 2);
        checkRepositoryLocaleStatistic(repositoryLocaleStatisticJaJP, "ja-JP", 0, 0, 0, 0, 0, 0, 0, 0, 1, 2);

        TMTextUnitVariant addTextUnitVariant = virtualAssetService.addTextUnitVariant(
                virtualAsset.getId(),
                repoLocaleFrFR.getLocale().getId(),
                "test2 name",
                "test2 content fr-FR",
                "");

        repositoryLocaleStatisticFrFR = repositoryStatisticService.computeLocaleStatistics(repoLocaleFrFR);
        checkRepositoryLocaleStatistic(repositoryLocaleStatisticFrFR, "fr-FR", 1, 2, 1,2,0,0,0,0,1,2);

        tmService.addTMTextUnitCurrentVariant(
                addTextUnitVariant.getTmTextUnit().getId(),
                repoLocaleFrFR.getLocale().getId(),
                "test2 content fr-FR",
                "",
                TMTextUnitVariant.Status.TRANSLATION_NEEDED,
                true);

        repositoryLocaleStatisticFrFR = repositoryStatisticService.computeLocaleStatistics(repoLocaleFrFR);
        checkRepositoryLocaleStatistic(repositoryLocaleStatisticFrFR, "fr-FR", 1, 2, 1, 2, 0, 0, 1,2,1,2);
    }

    private void checkTextUnitCounts(RepositoryStatistic repositoryStatistic) {
        assertEquals(2L, (long) repositoryStatistic.getUsedTextUnitCount());
        assertEquals(9L, (long) repositoryStatistic.getUsedTextUnitWordCount());
        assertEquals(1L, (long) repositoryStatistic.getUnusedTextUnitCount());
        assertEquals(1L, (long) repositoryStatistic.getUnusedTextUnitWordCount());
    }

    @Test
    public void testComputeLocaleStatistics() throws Exception {

        TMTestData tmTestData = new TMTestData(testIdWatcher);

        logger.debug("Mark one translated string as not included and needs review");

        tmService.addTMTextUnitCurrentVariant(
                tmTestData.addCurrentTMTextUnitVariant1FrFR.getTmTextUnit().getId(),
                tmTestData.frFR.getId(),
                tmTestData.addCurrentTMTextUnitVariant1FrFR.getContent(),
                "this translation fails compilation",
                TMTextUnitVariant.Status.REVIEW_NEEDED,
                false);

        tmService.addTMTextUnitCurrentVariant(
                tmTestData.addCurrentTMTextUnitVariant1KoKR.getTmTextUnit().getId(),
                tmTestData.koKR.getId(),
                tmTestData.addCurrentTMTextUnitVariant1KoKR.getContent(),
                "this translation fails compilation",
                TMTextUnitVariant.Status.TRANSLATION_NEEDED,
                true);

        RepositoryLocaleStatistic repositoryLocaleStatisticFrFR = repositoryStatisticService.computeLocaleStatistics(tmTestData.repoLocaleFrFR);
        RepositoryLocaleStatistic repositoryLocaleStatisticKoKR = repositoryStatisticService.computeLocaleStatistics(tmTestData.repoLocaleKoKR);

        checkRepositoryLocaleStatistic(repositoryLocaleStatisticFrFR, "fr-FR", 1, 8, 0, 0, 1, 8, 0, 0, 2, 9);
        checkRepositoryLocaleStatistic(repositoryLocaleStatisticKoKR, "ko-KR", 1, 8, 1, 8, 0, 0, 1, 8, 2, 9);
    }

    private void checkRepositoryLocaleStatistic(
            RepositoryLocaleStatistic repositoryLocaleStatistic,
            String expectedBcp47tag,
            long expectedTranslatedCount,
            long expectedTranslatedWordCount,
            long expectedIncludeInFileCount,
            long expectedIncludeInFileWordCount,
            long reviewNeededCount,
            long reviewNeededWordCount,
            long expectedTranslationNeededCount,
            long expectedTranslationNeededWordCount,
            long expectedForTranslationCount,
            long expectedForTranslationWordCount) {

        assertEquals("expectedBcp47tag", expectedBcp47tag, repositoryLocaleStatistic.getLocale().getBcp47Tag());
        assertEquals("expectedTranslatedCount", expectedTranslatedCount, (long) repositoryLocaleStatistic.getTranslatedCount());
        assertEquals("expectedTranslatedWordCount", expectedTranslatedWordCount, (long) repositoryLocaleStatistic.getTranslatedWordCount());
        assertEquals("expectedIncludeInFileCount", expectedIncludeInFileCount, (long) repositoryLocaleStatistic.getIncludeInFileCount());
        assertEquals("expectedIncludeInFileWordCount", expectedIncludeInFileWordCount, (long) repositoryLocaleStatistic.getIncludeInFileWordCount());
        assertEquals("reviewNeededCount", reviewNeededCount, (long) repositoryLocaleStatistic.getReviewNeededCount());
        assertEquals("reviewNeededWordCount", reviewNeededWordCount, (long) repositoryLocaleStatistic.getReviewNeededWordCount());
        assertEquals("expectedTranslationNeededCount", expectedTranslationNeededCount, (long) repositoryLocaleStatistic.getTranslationNeededCount());
        assertEquals("expectedTranslationNeededWordCount", expectedTranslationNeededWordCount, (long) repositoryLocaleStatistic.getTranslationNeededWordCount());
        assertEquals("expectedForTranslationCount", expectedForTranslationCount, (long) repositoryLocaleStatistic.getForTranslationCount());
        assertEquals("expectedForTranslationWordCount", expectedForTranslationWordCount, (long) repositoryLocaleStatistic.getForTranslationWordCount());
    }

}
