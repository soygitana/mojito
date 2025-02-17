package com.box.l10n.mojito.cli.command.checks;

import com.beust.jcommander.internal.Lists;
import com.box.l10n.mojito.cli.command.CommandException;
import com.box.l10n.mojito.cli.command.extraction.AssetExtractionDiff;
import com.box.l10n.mojito.io.Files;
import com.box.l10n.mojito.okapi.extractor.AssetExtractorTextUnit;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import dumonts.hunspell.Hunspell;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.box.l10n.mojito.cli.command.checks.CliCheckerParameters.DICTIONARY_ADDITIONS_PATH_KEY;
import static com.box.l10n.mojito.cli.command.checks.CliCheckerParameters.DICTIONARY_AFFIX_FILE_PATH_KEY;
import static com.box.l10n.mojito.cli.command.checks.CliCheckerParameters.DICTIONARY_FILE_PATH_KEY;
import static com.box.l10n.mojito.regex.PlaceholderRegularExpressions.PLACEHOLDER_NO_SPECIFIER_REGEX;
import static com.box.l10n.mojito.regex.PlaceholderRegularExpressions.PRINTF_LIKE_VARIABLE_TYPE_REGEX;
import static com.box.l10n.mojito.regex.PlaceholderRegularExpressions.SINGLE_BRACE_REGEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SpellCliCheckerTest {

    @Mock
    private Hunspell hunspellMock;

    @Spy
    private SpellCliChecker spellCliChecker = new SpellCliChecker();

    private List<AssetExtractionDiff> assetExtractionDiffs;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        doReturn(CliCheckerType.SPELL_CHECKER).when(spellCliChecker).getCliCheckerType();
        doReturn(hunspellMock).when(spellCliChecker).getHunspellInstance();
        when(hunspellMock.spell(isA(String.class))).thenReturn(true);
        when(hunspellMock.spell("strng")).thenReturn(false);
        when(hunspellMock.spell("erors")).thenReturn(false);
        when(hunspellMock.spell("falures")).thenReturn(false);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(SINGLE_BRACE_REGEX), Sets.newHashSet(), ImmutableMap.<String, String>builder()
                .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                .build()));
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with no errors.");
        addedTUs.add(assetExtractorTextUnit);
        assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
    }

    @Test
    public void testHardFailureIsSet() throws Exception {
        Set<CliCheckerType> hardFailureSet = new HashSet<>();
        hardFailureSet.add(CliCheckerType.SPELL_CHECKER);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(PLACEHOLDER_NO_SPECIFIER_REGEX), hardFailureSet, ImmutableMap.<String, String>builder()
                .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                .build()));
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertTrue(result.isHardFail());
    }

    @Test
    public void testStringWithNoErrors() throws Exception {
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
        assertFalse(result.isHardFail());
    }

    @Test
    public void testStringWithErrors() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source strng with some erors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        when(hunspellMock.spell("strng")).thenReturn(false);
        when(hunspellMock.spell("erors")).thenReturn(false);
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertFalse(result.isSuccessful());
        assertFalse(result.getNotificationText().isEmpty());
        assertTrue(result.getNotificationText().contains("* 'erors'"));
        assertTrue(result.getNotificationText().contains("* 'strng'"));
        assertFalse(result.isHardFail());
    }

    @Test
    public void testAddingStringsToDictionary() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source strng with some erors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        createTempDictionaryAdditionsFile("strng" + System.lineSeparator() + "erors");
        when(hunspellMock.spell("strng")).thenReturn(true);
        when(hunspellMock.spell("erors")).thenReturn(true);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(PLACEHOLDER_NO_SPECIFIER_REGEX), Sets.newHashSet(), ImmutableMap.<String, String>builder()
                .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                .put(DICTIONARY_ADDITIONS_PATH_KEY.getKey(), "target/tests/resources/dictAddition.txt")
                .build()));
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        verify(hunspellMock, times(1)).add("strng");
        verify(hunspellMock, times(1)).add("erors");
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
        assertFalse(result.isHardFail());
    }

    @Test
    public void testParametersAreNotSpellChecked() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with {image_name} errors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        verify(hunspellMock, times(0)).spell("image_name");
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
        assertFalse(result.isHardFail());
    }

    @Test
    public void testEmptyCurlyBracketsDoesNotCauseSubsequentPlaceholdersToBeIncludedInCheck() {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("Something went wrong {} saving the carousel images {imag_name} on our side. Please try again.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
        assertFalse(result.isHardFail());
    }

    @Test
    public void testAddingStringsToDictionaryAppendsSuggestedUpdateOnFailure() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source strng with some erors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        createTempDictionaryAdditionsFile("");
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(PLACEHOLDER_NO_SPECIFIER_REGEX), Sets.newHashSet(), ImmutableMap.<String, String>builder()
                .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                .put(DICTIONARY_ADDITIONS_PATH_KEY.getKey(), "target/tests/resources/dictAddition.txt")
                .build()));
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertFalse(result.isSuccessful());
        assertFalse(result.getNotificationText().isEmpty());
        assertTrue(result.getNotificationText().contains("* 'erors'"));
        assertTrue(result.getNotificationText().contains("* 'strng'"));
        assertTrue(result.getNotificationText().contains("If a word is correctly spelt please add your spelling to target/tests/resources/dictAddition.txt to avoid future false negatives."));
        assertFalse(result.isHardFail());
    }

    @Test
    public void testMultipleStringsWithMisspellings() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source strng with some erors.");
        addedTUs.add(assetExtractorTextUnit);
        AssetExtractorTextUnit anotherTextUnit = new AssetExtractorTextUnit();
        anotherTextUnit.setSource("Another string with falures");
        addedTUs.add(anotherTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertFalse(result.isSuccessful());
        assertFalse(result.getNotificationText().isEmpty());
        assertTrue(result.getNotificationText().contains("* 'erors'"));
        assertTrue(result.getNotificationText().contains("* 'strng'"));
        assertTrue(result.getNotificationText().contains("* 'falures'"));
    }

    @Test
    public void testNestedICUPlaceholderIsExcludedFromSpellcheck() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("You have {pagesCount, plral, one {# pazge.} othr {# pages.}}");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
        assertFalse(result.isHardFail());
    }

    @Test
    public void testMultipleNestedICUPlaceholderIsExcludedFromSpellcheck() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("You have {pagesCount, plral, one {# pazge.} othr {# pages.}} {anotherCount, plral, one {# count } othr { # cnt }}");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
        assertFalse(result.isHardFail());
    }

    @Test(expected = CommandException.class)
    public void testInvalidNumberClosingBracketsForPlaceholder() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("You have {pagesCount, plral, one {# pazge.} othr {# pages.}} {anotherCount, plral, one {# count } othr { # cnt }");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        spellCliChecker.run(assetExtractionDiffs);
    }

    @Test
    public void testMultipleIdenticalErrorsInStringAreReportedOnlyOnce() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with identicl identicl identicl errors.");
        addedTUs.add(assetExtractorTextUnit);
        when(hunspellMock.spell("identicl")).thenReturn(false);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertFalse(result.isSuccessful());
        assertTrue(result.getNotificationText().contains("* 'identicl'"));
        assertEquals(1, result.getNotificationText().chars().filter(c -> c == '*').count());
    }

    @Test
    public void testMultipleIdenticalErrorsInMultipleIdenticalStrings() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit1 = new AssetExtractorTextUnit();
        assetExtractorTextUnit1.setSource("A source string with identicl identicl identicl errors.");
        AssetExtractorTextUnit assetExtractorTextUnit2 = new AssetExtractorTextUnit();
        assetExtractorTextUnit2.setSource("A source string with identicl identicl identicl errors.");
        addedTUs.add(assetExtractorTextUnit1);
        addedTUs.add(assetExtractorTextUnit2);
        when(hunspellMock.spell("identicl")).thenReturn(false);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);

        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertFalse(result.isSuccessful());
        assertTrue(result.getNotificationText().contains("* 'identicl'"));
        assertEquals(1, result.getNotificationText().chars().filter(c -> c == '*').count());
    }

    @Test
    public void testMultipleConfiguredPlaceholderRegexesAreNotSpellChecked() throws Exception {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with a {numbr} of %(diferent)s errors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(SINGLE_BRACE_REGEX, PRINTF_LIKE_VARIABLE_TYPE_REGEX), Sets.newHashSet(),
                ImmutableMap.<String, String>builder()
                        .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                        .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                        .build()));
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertTrue(result.isSuccessful());
        assertTrue(result.getNotificationText().isEmpty());
    }

    @Test(expected = CommandException.class)
    public void testExceptionThrownIfNoDictionaryFileProvided() {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with a {numbr} of %(diferent)s errors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(SINGLE_BRACE_REGEX, PRINTF_LIKE_VARIABLE_TYPE_REGEX), Sets.newHashSet(),
                ImmutableMap.<String, String>builder()
                        .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                        .build()));
        spellCliChecker.run(assetExtractionDiffs);
    }

    @Test(expected = CommandException.class)
    public void testExceptionThrownIfNoDictAffixFileProvided() {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with a {numbr} of %(diferent)s errors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(SINGLE_BRACE_REGEX, PRINTF_LIKE_VARIABLE_TYPE_REGEX), Sets.newHashSet(),
                ImmutableMap.<String, String>builder()
                        .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                        .build()));
        spellCliChecker.run(assetExtractionDiffs);
    }

    @Test(expected = CommandException.class)
    public void testExceptionThrownIfDictFileDoesNotExist() {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with a {numbr} of %(diferent)s errors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(SINGLE_BRACE_REGEX, PRINTF_LIKE_VARIABLE_TYPE_REGEX), Sets.newHashSet(),
                ImmutableMap.<String, String>builder()
                        .put(DICTIONARY_FILE_PATH_KEY.getKey(), "fake_dir/dictionaries/empty.dic")
                        .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.aff")
                        .build()));
        spellCliChecker.run(assetExtractionDiffs);
    }

    @Test(expected = CommandException.class)
    public void testExceptionThrownIfDictAffixFileDoesNotExist() {
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A source string with a {numbr} of %(diferent)s errors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        spellCliChecker.setCliCheckerOptions(new CliCheckerOptions(Sets.newHashSet(SINGLE_BRACE_REGEX, PRINTF_LIKE_VARIABLE_TYPE_REGEX), Sets.newHashSet(),
                ImmutableMap.<String, String>builder()
                        .put(DICTIONARY_FILE_PATH_KEY.getKey(), "target/test-classes/dictionaries/empty.dic")
                        .put(DICTIONARY_AFFIX_FILE_PATH_KEY.getKey(), "fake_dir/dictionaries/empty.aff")
                        .build()));
        spellCliChecker.run(assetExtractionDiffs);
    }

    @Test
    public void testSuggestedSpellingsAreIncludedInNotificationMessgage() {
        when(hunspellMock.suggest("erors")).thenReturn(Lists.newArrayList("errors"));
        when(hunspellMock.suggest("strng")).thenReturn(Lists.newArrayList("string", "strong"));
        when(hunspellMock.suggest("sorce")).thenReturn(Lists.newArrayList("source", "sorcerer", "sorcery"));
        List<AssetExtractorTextUnit> addedTUs = new ArrayList<>();
        AssetExtractorTextUnit assetExtractorTextUnit = new AssetExtractorTextUnit();
        assetExtractorTextUnit.setSource("A sorce strng with some erors.");
        addedTUs.add(assetExtractorTextUnit);
        List<AssetExtractionDiff> assetExtractionDiffs = new ArrayList<>();
        AssetExtractionDiff assetExtractionDiff = new AssetExtractionDiff();
        assetExtractionDiff.setAddedTextunits(addedTUs);
        assetExtractionDiffs.add(assetExtractionDiff);
        when(hunspellMock.spell("strng")).thenReturn(false);
        when(hunspellMock.spell("erors")).thenReturn(false);
        when(hunspellMock.spell("sorce")).thenReturn(false);
        CliCheckResult result = spellCliChecker.run(assetExtractionDiffs);
        assertFalse(result.isSuccessful());
        assertFalse(result.getNotificationText().isEmpty());
        assertTrue(result.getNotificationText().contains("* 'erors'"));
        assertTrue(result.getNotificationText().contains("* 'strng'"));
        assertTrue(result.getNotificationText().contains("* 'sorce'"));
        assertTrue(result.getNotificationText().contains("Did you mean errors?"));
        assertTrue(result.getNotificationText().contains("Did you mean string or strong?"));
        assertTrue(result.getNotificationText().contains("Did you mean source, sorcerer or sorcery?"));
        assertFalse(result.isHardFail());
    }

    private void createTempDictionaryAdditionsFile(String contents) throws IOException {
        File dictAdditions = new File("target/tests/resources/");
        dictAdditions.mkdirs();
        dictAdditions = new File("target/tests/resources/dictAddition.txt");
        dictAdditions.createNewFile();
        Files.write(Paths.get("target/tests/resources/dictAddition.txt"), contents);
    }

}
