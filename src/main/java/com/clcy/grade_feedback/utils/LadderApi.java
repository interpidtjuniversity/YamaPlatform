package com.clcy.grade_feedback.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of the ladderpath algorithm used by ladder_api.py and
 * lppack/ladderpath_v2.py.
 *
 * <p>The public API mirrors the Python entry points that are useful without the
 * Graphviz drawing dependency:
 * <ul>
 *   <li>{@link #ladderRun(String)}: clean one text and return ladderpath JSON data.</li>
 *   <li>{@link #getAllNodes(String)}: return all ladderon strings discovered in one text.</li>
 *   <li>{@link #getLadderpath(List)} / {@link #getLadderpath(Map)}: lower-level APIs.</li>
 *   <li>{@link #toJson(Object)}: serialize the returned data without third-party JSON libraries.</li>
 * </ul>
 */
public final class LadderApi {
    private static final String INFO = "V1.0.2.20260622_Alpha";

    private LadderApi() {
    }

    public static Map<String, Object> ladderRun(String text) {
        return getLadderpath(Collections.singletonList(cleanText(text)), true, false);
    }

    public static String ladderRunJson(String text) {
        return toJson(ladderRun(text));
    }

    public static List<String> getAllNodes(String text) {
        Map<String, Object> lpjson = ladderRun(text);
        @SuppressWarnings("unchecked")
        Map<Integer, List<Object>> ladderons = (Map<Integer, List<Object>>) lpjson.get("ladderons");
        List<String> allNodes = new ArrayList<>();
        for (List<Object> info : ladderons.values()) {
            if (info.size() >= 3 && info.get(2) instanceof String && !((String) info.get(2)).isEmpty()) {
                allNodes.add((String) info.get(2));
            }
        }
        return allNodes;
    }

    public static Map<String, Object> getLadderpath(List<String> targets) {
        return getLadderpath(targets, false, true);
    }

    public static Map<String, Object> getLadderpath(List<String> targets, boolean fillLadderonStrings, boolean showVersion) {
        if (!validInput(targets)) {
            throw new IllegalArgumentException("Input is not valid: every target string must have length > 1.");
        }
        if (showVersion) {
            System.out.println("This version of the ladderpath JSON format is: " + INFO);
        }

        UniqueResult unique = uniquenizeList(targets);
        FindResult found = findLadderpath(unique.uniqueStrings);
        Map<String, Object> data = buildBaseJson("list", found.ladderpathIndex, found.totalTargetLength);
        fillDataFromLadderons(data, found.allLadderons, unique.uniqueStrings.size());
        applyListDuplications(data, unique.duplicationsInfo);

        if (fillLadderonStrings) {
            fillLpjsonStrings(data);
        }
        return data;
    }

    public static Map<String, Object> getLadderpath(Map<String, Integer> targets) {
        return getLadderpath(targets, false, true);
    }

    public static Map<String, Object> getLadderpath(Map<String, Integer> targets, boolean fillLadderonStrings, boolean showVersion) {
        List<String> keys = new ArrayList<>(targets.keySet());
        if (!validInput(keys)) {
            throw new IllegalArgumentException("Input is not valid: every target string must have length > 1.");
        }
        if (showVersion) {
            System.out.println("This version of the ladderpath JSON format is: " + INFO);
        }

        UniqueDictResult unique = uniquenizeDict(targets);
        FindResult found = findLadderpath(unique.uniqueStrings);
        Map<String, Object> data = buildBaseJson("dict", found.ladderpathIndex, found.totalTargetLength);
        fillDataFromLadderons(data, found.allLadderons, unique.uniqueStrings.size());
        applyDictDuplications(data, unique.duplicationsInfo);

        if (fillLadderonStrings) {
            fillLpjsonStrings(data);
        }
        return data;
    }

    private static String cleanText(String text) {
        Set<Character> stopWords = new HashSet<>();
        for (char c : new char[] {'\u55ef', '\u5443', ' ', '\uff0c', '\u3002', '\n', '\r', '\t'}) {
            stopWords.add(c);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!stopWords.contains(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Map<String, Object> buildBaseJson(String inputType, int ladderpathIndex, int totalTargetLength) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("info", INFO);
        data.put("ladderpath-index", ladderpathIndex);
        data.put("order-index", totalTargetLength - ladderpathIndex);
        data.put("size-index", totalTargetLength);
        data.put("eta", null);
        data.put("ladderons", new LinkedHashMap<Integer, List<Object>>());
        data.put("basic_building_blocks", new ArrayList<String>());
        data.put("targets", new LinkedHashMap<Integer, List<Object>>());
        data.put("duplications_info", new LinkedHashMap<Integer, Object>());

        Map<String, Object> etaInfo = new LinkedHashMap<>();
        etaInfo.put("omega_max_AllIdentical", null);
        etaInfo.put("omega_max_Sorted", null);
        etaInfo.put("omega_min_Shuffle_list", new ArrayList<Object>());
        etaInfo.put("omega_min_LocalDist_list", new ArrayList<Object>());
        etaInfo.put("omega_min_EvenDist_list", new ArrayList<Object>());
        data.put("eta_info", etaInfo);
        data.put("input_type", inputType);
        return data;
    }

    @SuppressWarnings("unchecked")
    private static void fillDataFromLadderons(Map<String, Object> data, List<Ladderon> allLadderons, int targetCount) {
        List<String> basicBlocks = (List<String>) data.get("basic_building_blocks");
        int numBasicBlocks = 0;
        for (Ladderon ladderon : allLadderons) {
            if (ladderon.text.length() == 1) {
                basicBlocks.add(ladderon.text);
                numBasicBlocks++;
            }
        }

        Map<Integer, List<Object>> ladderons = (Map<Integer, List<Object>>) data.get("ladderons");
        Map<Integer, List<Object>> targets = (Map<Integer, List<Object>>) data.get("targets");
        for (int i = 0; i < allLadderons.size() - numBasicBlocks; i++) {
            Ladderon ladderon = allLadderons.get(i);
            int newId = ladderon.id < targetCount ? -ladderon.id - 1 : ladderon.id - targetCount;

            List<Object> newComp = new ArrayList<>();
            for (Integer componentId : ladderon.comp) {
                Ladderon component = allLadderons.get(componentId);
                if (component.text.length() == 1) {
                    String basic = component.text;
                    int last = newComp.size() - 1;
                    if (last >= 0 && newComp.get(last) instanceof String) {
                        newComp.set(last, ((String) newComp.get(last)) + basic);
                    } else {
                        newComp.add(basic);
                    }
                } else if (componentId < targetCount) {
                    newComp.add(-componentId - 1);
                } else {
                    newComp.add(componentId - targetCount);
                }
            }

            if (ladderon.pos.isEmpty()) {
                List<Object> info = new ArrayList<>();
                info.add(newComp);
                info.add(ladderon.text.length());
                info.add("");
                info.add(1);
                targets.put(newId, info);
            } else {
                Map<Integer, List<Integer>> newPos = new LinkedHashMap<>();
                for (Map.Entry<Integer, List<Integer>> entry : ladderon.pos.entrySet()) {
                    int parentId = entry.getKey() < targetCount ? -entry.getKey() - 1 : entry.getKey() - targetCount;
                    newPos.put(parentId, new ArrayList<>(entry.getValue()));
                }
                List<Object> info = new ArrayList<>();
                info.add(newComp);
                info.add(ladderon.text.length());
                info.add("");
                info.add(newPos);
                ladderons.put(newId, info);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyListDuplications(Map<String, Object> data, Map<String, List<Integer>> duplicationsInfo) {
        if (duplicationsInfo.isEmpty()) {
            return;
        }
        Map<Integer, List<Object>> ladderons = (Map<Integer, List<Object>>) data.get("ladderons");
        Map<Integer, List<Object>> targets = (Map<Integer, List<Object>>) data.get("targets");
        Map<Integer, Object> tempDupInfo = new LinkedHashMap<>();

        for (List<Integer> val : duplicationsInfo.values()) {
            int targetId = val.get(0);
            int extraDuplications = val.size() - 2;
            tempDupInfo.put(targetId, new ArrayList<>(val.subList(1, val.size())));
            List<Object> targetInfo = targets.get(targetId);
            targetInfo.set(3, ((Integer) targetInfo.get(3)) + extraDuplications);
            data.put("ladderpath-index", ((Integer) data.get("ladderpath-index")) + extraDuplications);
            data.put("size-index", ((Integer) data.get("size-index")) + extraDuplications * ((Integer) targetInfo.get(1)));

            @SuppressWarnings("unchecked")
            List<Object> comp = (List<Object>) targetInfo.get(0);
            if (needsExtraLadderon(comp)) {
                int newLadderonId = ladderons.size();
                List<Object> newInfo = new ArrayList<>(targetInfo.subList(0, 3));
                Map<Integer, List<Integer>> pos = new LinkedHashMap<>();
                pos.put(targetId, Collections.singletonList(0));
                newInfo.add(pos);
                ladderons.put(newLadderonId, newInfo);

                for (Map.Entry<Integer, List<Object>> entry : ladderons.entrySet()) {
                    if (entry.getKey() == newLadderonId) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<Integer, List<Integer>> oldPos = (Map<Integer, List<Integer>>) entry.getValue().get(3);
                    if (oldPos.containsKey(targetId)) {
                        oldPos.put(newLadderonId, oldPos.remove(targetId));
                    }
                }
            }
        }
        data.put("order-index", ((Integer) data.get("size-index")) - ((Integer) data.get("ladderpath-index")));
        data.put("duplications_info", tempDupInfo);
    }

    @SuppressWarnings("unchecked")
    private static void applyDictDuplications(Map<String, Object> data, Map<Integer, Integer> duplicationsInfo) {
        if (duplicationsInfo.isEmpty()) {
            return;
        }
        Map<Integer, List<Object>> ladderons = (Map<Integer, List<Object>>) data.get("ladderons");
        Map<Integer, List<Object>> targets = (Map<Integer, List<Object>>) data.get("targets");
        Map<Integer, Object> tempDupInfo = new LinkedHashMap<>();

        for (Map.Entry<Integer, Integer> dup : duplicationsInfo.entrySet()) {
            int targetId = dup.getKey();
            int extraDuplications = dup.getValue() - 1;
            tempDupInfo.put(targetId, dup.getValue());
            List<Object> targetInfo = targets.get(targetId);
            targetInfo.set(3, ((Integer) targetInfo.get(3)) + extraDuplications);
            data.put("ladderpath-index", ((Integer) data.get("ladderpath-index")) + extraDuplications);
            data.put("size-index", ((Integer) data.get("size-index")) + extraDuplications * ((Integer) targetInfo.get(1)));

            @SuppressWarnings("unchecked")
            List<Object> comp = (List<Object>) targetInfo.get(0);
            if (needsExtraLadderon(comp)) {
                int newLadderonId = ladderons.size();
                List<Object> newInfo = new ArrayList<>(targetInfo.subList(0, 3));
                Map<Integer, List<Integer>> pos = new LinkedHashMap<>();
                pos.put(targetId, Collections.singletonList(0));
                newInfo.add(pos);
                ladderons.put(newLadderonId, newInfo);

                for (Map.Entry<Integer, List<Object>> entry : ladderons.entrySet()) {
                    if (entry.getKey() == newLadderonId) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<Integer, List<Integer>> oldPos = (Map<Integer, List<Integer>>) entry.getValue().get(3);
                    if (oldPos.containsKey(targetId)) {
                        oldPos.put(newLadderonId, oldPos.remove(targetId));
                    }
                }
            }
        }
        data.put("order-index", ((Integer) data.get("size-index")) - ((Integer) data.get("ladderpath-index")));
        data.put("duplications_info", tempDupInfo);
    }

    private static boolean needsExtraLadderon(List<Object> comp) {
        if (comp.size() > 1) {
            return true;
        }
        Object only = comp.get(0);
        if (only instanceof Integer) {
            return false;
        }
        return ((String) only).length() > 1;
    }

    private static FindResult findLadderpath(List<String> targets) {
        int totalTargetLength = 0;
        for (String target : targets) {
            totalTargetLength += target.length();
        }

        List<Ladderon> allLadderons = new ArrayList<>();
        List<LadderonRef> segmentRefs = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Ladderon ladderon = new Ladderon(i, targets.get(i));
            allLadderons.add(ladderon);
            segmentRefs.add(ladderon.makeRef());
        }

        int ladderpathIndex = 0;
        List<Map<Integer, List<Integer>>> replacementLuts = new ArrayList<>();
        long componentStart = System.nanoTime();
        List<LinkedHashMap<String, List<Occurrence>>> componentLevels = findRepeatedSubstringsByLevel(segmentRefs);
        long componentEnd = System.nanoTime();
        boolean needRecompute = false;

        int currentLevel = componentLevels.size() - 1;
        boolean newLevel = true;
        while (currentLevel > 0) {
            if (needRecompute) {
                replacementLuts.clear();
                componentStart = System.nanoTime();
                componentLevels = findRepeatedSubstringsByLevel(segmentRefs);
                componentEnd = System.nanoTime();
                needRecompute = false;
                if (componentLevels.size() - 1 > currentLevel) {
                    throw new IllegalStateException("Recomputed component level increased unexpectedly.");
                }
                if (currentLevel != componentLevels.size() - 1) {
                    newLevel = true;
                }
                currentLevel = componentLevels.size() - 1;
                if (currentLevel <= 0) {
                    break;
                }
            }
            if (newLevel) {
                // Matches the Python variable reset. The count is not emitted.
                int ignoredLevelComponentCount = 0;
                ignoredLevelComponentCount++;
            }
            newLevel = false;

            PythonHeap heap = new PythonHeap();
            for (Map.Entry<String, List<Occurrence>> entry : componentLevels.get(currentLevel).entrySet()) {
                heap.addRaw(new Pattern(entry.getKey(), 0, entry.getValue()));
            }
            heap.heapify();

            while (true) {
                if (heap.isEmpty()) {
                    newLevel = true;
                    break;
                }
                Pattern bestPattern = null;
                List<Pattern> refreshedPatterns = new ArrayList<>();

                while (bestPattern == null || bestPattern.key() < heap.peek().key()) {
                    Pattern nextCandidate = heap.pop();
                    List<Occurrence> refreshed = refreshPatternOccurrences(segmentRefs, replacementLuts, nextCandidate);
                    if (refreshed != null) {
                        Pattern refreshedPattern = new Pattern(nextCandidate.text, replacementLuts.size(), refreshed);
                        refreshedPatterns.add(refreshedPattern);
                        if (bestPattern == null || refreshed.size() > bestPattern.occurrences.size()) {
                            bestPattern = refreshedPattern;
                        }
                    }
                    if (heap.isEmpty()) {
                        break;
                    }
                }

                if (System.nanoTime() - componentEnd > componentEnd - componentStart) {
                    needRecompute = true;
                }
                if (bestPattern == null) {
                    newLevel = true;
                    break;
                }
                for (Pattern refreshedPattern : refreshedPatterns) {
                    if (refreshedPattern != bestPattern) {
                        heap.push(refreshedPattern);
                    }
                }

                ReplaceResult replaced = replacePatternWithLadderon(segmentRefs, bestPattern, allLadderons);
                segmentRefs = replaced.segmentRefs;
                ladderpathIndex += replaced.savedLp;
                replacementLuts.add(replaced.replacementLut);
                if (needRecompute) {
                    break;
                }
            }
            if (newLevel) {
                currentLevel--;
            }
        }

        ladderpathIndex += expandRemainingSegmentsToBaseLevel(segmentRefs, allLadderons);
        for (Ladderon ladderon : allLadderons) {
            ladderon.compWithPos.sort(Comparator.comparingInt(cp -> cp.position));
            ladderon.comp.clear();
            for (ComponentAt componentAt : ladderon.compWithPos) {
                ladderon.comp.add(componentAt.componentId);
            }
        }
        return new FindResult(ladderpathIndex, totalTargetLength, allLadderons);
    }

    private static List<LinkedHashMap<String, List<Occurrence>>> findRepeatedSubstringsByLevel(List<LadderonRef> segmentRefs) {
        LinkedHashMap<String, List<Occurrence>> currentLevel = new LinkedHashMap<>();
        for (int segmentIndex = 0; segmentIndex < segmentRefs.size(); segmentIndex++) {
            LadderonRef segmentRef = segmentRefs.get(segmentIndex);
            if (segmentRef == null) {
                continue;
            }
            for (int charIndex = 0; charIndex < segmentRef.text.length(); charIndex++) {
                String ch = segmentRef.text.substring(charIndex, charIndex + 1);
                currentLevel.computeIfAbsent(ch, k -> new ArrayList<>()).add(new Occurrence(segmentIndex, charIndex));
            }
        }

        List<LinkedHashMap<String, List<Occurrence>>> allLevels = new ArrayList<>();
        while (true) {
            allLevels.add(currentLevel);
            LinkedHashMap<String, List<Occurrence>> nextLevel = new LinkedHashMap<>();
            for (Map.Entry<String, List<Occurrence>> entry : currentLevel.entrySet()) {
                String componentText = entry.getKey();
                int componentLength = componentText.length();
                LinkedHashMap<String, List<Occurrence>> expandedCandidates = new LinkedHashMap<>();
                for (Occurrence occurrence : entry.getValue()) {
                    LadderonRef ref = segmentRefs.get(occurrence.segmentIndex);
                    if (occurrence.startIndex + componentLength >= ref.text.length()) {
                        continue;
                    }
                    String expanded = ref.text.substring(occurrence.startIndex, occurrence.startIndex + componentLength + 1);
                    expandedCandidates.computeIfAbsent(expanded, k -> new ArrayList<>()).add(occurrence);
                }
                for (Map.Entry<String, List<Occurrence>> expanded : expandedCandidates.entrySet()) {
                    List<Occurrence> occurrences = expanded.getValue();
                    if (occurrences.size() <= 1) {
                        continue;
                    }
                    Set<Integer> segmentIds = new HashSet<>();
                    int min = Integer.MAX_VALUE;
                    int max = Integer.MIN_VALUE;
                    for (Occurrence occurrence : occurrences) {
                        segmentIds.add(occurrence.segmentIndex);
                        min = Math.min(min, occurrence.startIndex);
                        max = Math.max(max, occurrence.startIndex);
                    }
                    if (segmentIds.size() == 1 && max - min < componentLength + 1) {
                        continue;
                    }
                    nextLevel.computeIfAbsent(expanded.getKey(), k -> new ArrayList<>()).addAll(occurrences);
                }
            }
            if (nextLevel.isEmpty()) {
                break;
            }
            currentLevel = nextLevel;
        }
        return allLevels;
    }

    private static List<Integer> findAllOccurrences(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        int searchStart = 0;
        while (true) {
            int found = text.indexOf(pattern, searchStart);
            if (found < 0) {
                return positions;
            }
            positions.add(found);
            searchStart = found + 1;
        }
    }

    private static List<Occurrence> refreshPatternOccurrences(
            List<LadderonRef> segmentRefs,
            List<Map<Integer, List<Integer>>> replacementLuts,
            Pattern pattern) {
        if (pattern.update >= replacementLuts.size()) {
            if (pattern.update != replacementLuts.size()) {
                throw new IllegalStateException("Pattern update is inconsistent with replacement history.");
            }
            return pattern.occurrences;
        }

        List<Occurrence> refreshed = new ArrayList<>();
        LinkedHashMap<Integer, List<Integer>> grouped = new LinkedHashMap<>();
        for (Occurrence occurrence : pattern.occurrences) {
            grouped.computeIfAbsent(occurrence.segmentIndex, k -> new ArrayList<>()).add(occurrence.startIndex);
        }

        for (Map.Entry<Integer, List<Integer>> entry : grouped.entrySet()) {
            int segmentIndex = entry.getKey();
            boolean needUpdate = false;
            for (int lutIndex = pattern.update; lutIndex < replacementLuts.size(); lutIndex++) {
                if (replacementLuts.get(lutIndex).containsKey(segmentIndex)) {
                    needUpdate = true;
                    break;
                }
            }
            if (needUpdate) {
                Set<Integer> candidates = new LinkedHashSet<>();
                candidates.add(segmentIndex);
                for (int lutIndex = pattern.update; lutIndex < replacementLuts.size(); lutIndex++) {
                    Set<Integer> nextCandidates = new LinkedHashSet<>();
                    Map<Integer, List<Integer>> lut = replacementLuts.get(lutIndex);
                    for (Integer currentSegment : candidates) {
                        if (lut.containsKey(currentSegment)) {
                            nextCandidates.addAll(lut.get(currentSegment));
                        } else {
                            nextCandidates.add(currentSegment);
                        }
                    }
                    candidates = nextCandidates;
                }
                for (Integer currentSegment : candidates) {
                    LadderonRef ref = segmentRefs.get(currentSegment);
                    if (ref == null) {
                        continue;
                    }
                    for (Integer pos : findAllOccurrences(ref.text, pattern.text)) {
                        refreshed.add(new Occurrence(currentSegment, pos));
                    }
                }
            } else {
                for (Integer startIndex : entry.getValue()) {
                    refreshed.add(new Occurrence(segmentIndex, startIndex));
                }
            }
        }

        if (refreshed.size() <= 1) {
            return null;
        }
        Set<Integer> segmentIds = new HashSet<>();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Occurrence occurrence : refreshed) {
            segmentIds.add(occurrence.segmentIndex);
            min = Math.min(min, occurrence.startIndex);
            max = Math.max(max, occurrence.startIndex);
        }
        if (segmentIds.size() == 1 && max - min < pattern.text.length()) {
            return null;
        }
        return refreshed;
    }

    private static int expandRemainingSegmentsToBaseLevel(List<LadderonRef> segmentRefs, List<Ladderon> allLadderons) {
        Map<String, Ladderon> baseComponents = new LinkedHashMap<>();
        int remainingLength = 0;
        for (LadderonRef segmentRef : segmentRefs) {
            if (segmentRef == null) {
                continue;
            }
            remainingLength += segmentRef.text.length();
            for (int offset = 0; offset < segmentRef.text.length(); offset++) {
                String ch = segmentRef.text.substring(offset, offset + 1);
                Ladderon base = baseComponents.get(ch);
                if (base == null) {
                    base = new Ladderon(allLadderons.size(), ch);
                    baseComponents.put(ch, base);
                    allLadderons.add(base);
                }
                base.pos.computeIfAbsent(segmentRef.ladderon.id, k -> new ArrayList<>()).add(segmentRef.start + offset);
                segmentRef.ladderon.compWithPos.add(new ComponentAt(segmentRef.start + offset, base.id));
            }
        }
        return remainingLength;
    }

    private static ReplaceResult replacePatternWithLadderon(
            List<LadderonRef> segmentRefs,
            Pattern pattern,
            List<Ladderon> allLadderons) {
        LinkedHashMap<Integer, List<Integer>> positionsBySegment = new LinkedHashMap<>();
        for (Occurrence occurrence : pattern.occurrences) {
            positionsBySegment.computeIfAbsent(occurrence.segmentIndex, k -> new ArrayList<>()).add(occurrence.startIndex);
        }

        int replacedCount = 0;
        Map<Integer, List<Integer>> replacementLut = new LinkedHashMap<>();
        Ladderon newLadderon = new Ladderon(allLadderons.size(), pattern.text);

        for (Map.Entry<Integer, List<Integer>> entry : positionsBySegment.entrySet()) {
            int segmentIndex = entry.getKey();
            List<Integer> startPositions = new ArrayList<>(entry.getValue());
            Collections.sort(startPositions);
            LadderonRef segmentRef = segmentRefs.get(segmentIndex);
            List<LadderonRef> newFragments = new ArrayList<>();
            replacementLut.put(segmentIndex, new ArrayList<>());

            int lastConsumed = 0;
            for (Integer startIndex : startPositions) {
                if (startIndex + pattern.text.length() > segmentRef.text.length()) {
                    throw new IllegalStateException("Pattern occurrence exceeds segment length.");
                }
                String actual = segmentRef.text.substring(startIndex, startIndex + pattern.text.length());
                if (!actual.equals(pattern.text)) {
                    throw new IllegalStateException("Pattern occurrence text mismatch.");
                }
                if (startIndex < lastConsumed) {
                    continue;
                }
                if (startIndex > lastConsumed) {
                    newFragments.add(segmentRef.slice(lastConsumed, startIndex));
                }
                replacedCount++;
                newLadderon.pos.computeIfAbsent(segmentRef.ladderon.id, k -> new ArrayList<>())
                        .add(segmentRef.start + startIndex);
                segmentRef.ladderon.compWithPos.add(new ComponentAt(segmentRef.start + startIndex, newLadderon.id));
                lastConsumed = startIndex + pattern.text.length();
            }
            if (lastConsumed < segmentRef.text.length()) {
                newFragments.add(segmentRef.slice(lastConsumed, segmentRef.text.length()));
            }
            if (newFragments.isEmpty()) {
                segmentRefs.set(segmentIndex, null);
            } else {
                segmentRefs.set(segmentIndex, newFragments.get(0));
                replacementLut.get(segmentIndex).add(segmentIndex);
            }
            int originalSizeBeforeExtend = segmentRefs.size();
            for (int i = 1; i < newFragments.size(); i++) {
                segmentRefs.add(newFragments.get(i));
            }
            for (int newIndex = originalSizeBeforeExtend; newIndex < segmentRefs.size(); newIndex++) {
                replacementLut.get(segmentIndex).add(newIndex);
            }
        }

        segmentRefs.add(newLadderon.makeRef());
        int newLadderonRefIndex = segmentRefs.size() - 1;
        for (Integer segmentIndex : positionsBySegment.keySet()) {
            replacementLut.get(segmentIndex).add(newLadderonRefIndex);
        }
        if (replacedCount <= 1) {
            throw new IllegalStateException("A ladderon must replace at least two occurrences.");
        }
        allLadderons.add(newLadderon);
        return new ReplaceResult(segmentRefs, replacementLut, replacedCount - 1);
    }

    private static boolean validInput(List<String> targets) {
        for (String target : targets) {
            if (target == null || target.length() <= 1) {
                return false;
            }
        }
        return true;
    }

    private static UniqueResult uniquenizeList(List<String> strs) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String s : strs) {
            counter.put(s, counter.getOrDefault(s, 0) + 1);
        }
        if (strs.size() == counter.size()) {
            return new UniqueResult(new ArrayList<>(strs), new LinkedHashMap<>());
        }

        List<String> uniqueStrings = new ArrayList<>();
        Map<String, List<Integer>> duplicationsInfo = new LinkedHashMap<>();
        for (int i = 0; i < strs.size(); i++) {
            String s = strs.get(i);
            if (counter.get(s) == 1) {
                uniqueStrings.add(s);
            } else if (duplicationsInfo.containsKey(s)) {
                duplicationsInfo.get(s).add(i);
            } else {
                List<Integer> info = new ArrayList<>();
                info.add(-uniqueStrings.size() - 1);
                info.add(i);
                duplicationsInfo.put(s, info);
                uniqueStrings.add(s);
            }
        }
        return new UniqueResult(uniqueStrings, duplicationsInfo);
    }

    private static UniqueDictResult uniquenizeDict(Map<String, Integer> strs) {
        Map<Integer, Integer> duplicationsInfo = new LinkedHashMap<>();
        List<String> uniqueStrings = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> entry : strs.entrySet()) {
            i -= 1;
            uniqueStrings.add(entry.getKey());
            if (entry.getValue() > 1) {
                duplicationsInfo.put(i, entry.getValue());
            }
        }
        return new UniqueDictResult(uniqueStrings, duplicationsInfo);
    }

    @SuppressWarnings("unchecked")
    public static void fillLpjsonStrings(Map<String, Object> lpjson) {
        Map<Integer, List<Object>> ladderons = (Map<Integer, List<Object>>) lpjson.get("ladderons");
        List<Integer> ids = new ArrayList<>(ladderons.keySet());
        Collections.reverse(ids);
        for (Integer id : ids) {
            ladderons.get(id).set(2, fill(id, lpjson));
        }

        Map<Integer, List<Object>> targets = (Map<Integer, List<Object>>) lpjson.get("targets");
        for (List<Object> info : targets.values()) {
            String existing = (String) info.get(2);
            if (!existing.isEmpty()) {
                if (existing.length() != (Integer) info.get(1)) {
                    throw new IllegalStateException("Filled target string length mismatch.");
                }
                continue;
            }
            StringBuilder sb = new StringBuilder();
            List<Object> comp = (List<Object>) info.get(0);
            for (Object c : comp) {
                if (c instanceof Integer) {
                    sb.append((String) ladderons.get((Integer) c).get(2));
                } else {
                    sb.append((String) c);
                }
            }
            info.set(2, sb.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static String fill(Integer ladderonId, Map<String, Object> lpjson) {
        Map<Integer, List<Object>> ladderons = (Map<Integer, List<Object>>) lpjson.get("ladderons");
        List<Object> info = ladderons.get(ladderonId);
        String existing = (String) info.get(2);
        if (!existing.isEmpty()) {
            if (existing.length() != (Integer) info.get(1)) {
                throw new IllegalStateException("Filled ladderon string length mismatch.");
            }
            return existing;
        }
        StringBuilder sb = new StringBuilder();
        List<Object> comp = (List<Object>) info.get(0);
        for (Object c : comp) {
            if (c instanceof Integer) {
                sb.append(fill((Integer) c, lpjson));
            } else {
                sb.append((String) c);
            }
        }
        String text = sb.toString();
        info.set(2, text);
        return text;
    }

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append('"').append(escapeJson((String) value)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?>) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append('"').append(':');
                appendJson(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?>) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendJson(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escapeJson(String.valueOf(value))).append('"');
        }
    }

    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static final class Ladderon {
        final int id;
        final String text;
        final Map<Integer, List<Integer>> pos = new LinkedHashMap<>();
        final List<ComponentAt> compWithPos = new ArrayList<>();
        final List<Integer> comp = new ArrayList<>();

        Ladderon(int id, String text) {
            this.id = id;
            this.text = text;
        }

        LadderonRef makeRef() {
            return new LadderonRef(this, 0, text.length(), text);
        }
    }

    private static final class LadderonRef {
        final Ladderon ladderon;
        final int start;
        final int end;
        final String text;

        LadderonRef(Ladderon ladderon, int start, int end, String text) {
            this.ladderon = ladderon;
            this.start = start;
            this.end = end;
            this.text = text;
        }

        LadderonRef slice(int startOffset, int endOffset) {
            return new LadderonRef(ladderon, start + startOffset, start + endOffset, text.substring(startOffset, endOffset));
        }
    }

    private static final class Occurrence {
        final int segmentIndex;
        final int startIndex;

        Occurrence(int segmentIndex, int startIndex) {
            this.segmentIndex = segmentIndex;
            this.startIndex = startIndex;
        }
    }

    private static final class Pattern {
        final String text;
        final int update;
        final List<Occurrence> occurrences;

        Pattern(String text, int update, List<Occurrence> occurrences) {
            this.text = text;
            this.update = update;
            this.occurrences = occurrences;
        }

        int key() {
            return occurrences.size();
        }

        boolean lessThan(Pattern other) {
            return key() > other.key();
        }
    }

    private static final class PythonHeap {
        final List<Pattern> heap = new ArrayList<>();

        void addRaw(Pattern pattern) {
            heap.add(pattern);
        }

        void heapify() {
            for (int i = heap.size() / 2 - 1; i >= 0; i--) {
                siftUp(i);
            }
        }

        boolean isEmpty() {
            return heap.isEmpty();
        }

        Pattern peek() {
            return heap.get(0);
        }

        void push(Pattern pattern) {
            heap.add(pattern);
            siftDown(0, heap.size() - 1);
        }

        Pattern pop() {
            Pattern last = heap.remove(heap.size() - 1);
            if (heap.isEmpty()) {
                return last;
            }
            Pattern result = heap.get(0);
            heap.set(0, last);
            siftUp(0);
            return result;
        }

        private void siftDown(int startPos, int pos) {
            Pattern newItem = heap.get(pos);
            while (pos > startPos) {
                int parentPos = (pos - 1) >>> 1;
                Pattern parent = heap.get(parentPos);
                if (newItem.lessThan(parent)) {
                    heap.set(pos, parent);
                    pos = parentPos;
                    continue;
                }
                break;
            }
            heap.set(pos, newItem);
        }

        private void siftUp(int pos) {
            int endPos = heap.size();
            int startPos = pos;
            Pattern newItem = heap.get(pos);
            int childPos = 2 * pos + 1;
            while (childPos < endPos) {
                int rightPos = childPos + 1;
                if (rightPos < endPos && !heap.get(childPos).lessThan(heap.get(rightPos))) {
                    childPos = rightPos;
                }
                heap.set(pos, heap.get(childPos));
                pos = childPos;
                childPos = 2 * pos + 1;
            }
            heap.set(pos, newItem);
            siftDown(startPos, pos);
        }
    }

    private static final class ComponentAt {
        final int position;
        final int componentId;

        ComponentAt(int position, int componentId) {
            this.position = position;
            this.componentId = componentId;
        }
    }

    private static final class FindResult {
        final int ladderpathIndex;
        final int totalTargetLength;
        final List<Ladderon> allLadderons;

        FindResult(int ladderpathIndex, int totalTargetLength, List<Ladderon> allLadderons) {
            this.ladderpathIndex = ladderpathIndex;
            this.totalTargetLength = totalTargetLength;
            this.allLadderons = allLadderons;
        }
    }

    private static final class ReplaceResult {
        final List<LadderonRef> segmentRefs;
        final Map<Integer, List<Integer>> replacementLut;
        final int savedLp;

        ReplaceResult(List<LadderonRef> segmentRefs, Map<Integer, List<Integer>> replacementLut, int savedLp) {
            this.segmentRefs = segmentRefs;
            this.replacementLut = replacementLut;
            this.savedLp = savedLp;
        }
    }

    private static final class UniqueResult {
        final List<String> uniqueStrings;
        final Map<String, List<Integer>> duplicationsInfo;

        UniqueResult(List<String> uniqueStrings, Map<String, List<Integer>> duplicationsInfo) {
            this.uniqueStrings = uniqueStrings;
            this.duplicationsInfo = duplicationsInfo;
        }
    }

    private static final class UniqueDictResult {
        final List<String> uniqueStrings;
        final Map<Integer, Integer> duplicationsInfo;

        UniqueDictResult(List<String> uniqueStrings, Map<Integer, Integer> duplicationsInfo) {
            this.uniqueStrings = uniqueStrings;
            this.duplicationsInfo = duplicationsInfo;
        }
    }
}
