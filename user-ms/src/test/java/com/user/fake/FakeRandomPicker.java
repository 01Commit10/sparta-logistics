package com.user.fake;

import com.user.domain.service.RandomPicker;

import java.util.ArrayList;
import java.util.List;

/** 정해 둔 위치(기본 0번째)를 고른다. 위치가 후보 수보다 크면 마지막을 고른다. 받은 후보 목록을 순서대로 기록한다 */
public class FakeRandomPicker implements RandomPicker {

    private final List<List<?>> received = new ArrayList<>();
    private int index;

    public void pickIndex(int index) {
        this.index = index;
    }

    @Override
    public <T> T pick(List<T> candidates) {
        received.add(List.copyOf(candidates));
        return candidates.get(Math.min(index, candidates.size() - 1));
    }

    public List<List<?>> received() {
        return received;
    }
}
