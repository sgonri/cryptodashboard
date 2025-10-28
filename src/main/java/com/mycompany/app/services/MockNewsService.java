package com.mycompany.app.services;

import java.util.ArrayList;
import java.util.List;

import com.mycompany.app.models.News;

public class MockNewsService implements NewsService {
    @Override
    public List<News> getNewsForCrypto(String cryptoId) {
        List<News> list = new ArrayList<>();
        list.add(new News(cryptoId.toUpperCase() + " hits new high of $64,000", "CoinDesk", "1h ago"));
        list.add(new News("ETH ETF approved by SEC", "TheBlock", "2h ago"));
        list.add(new News("Solana up 10% after All-Time High", "Decrypt", "2h ago"));
        list.add(new News("NFT sales remain strong: report", "CoinTelegraph", "3h ago"));
        return list;
    }

    @Override
    public List<News> getGeneralNews() {
        List<News> list = new ArrayList<>();
        list.add(new News("BTC hits new high of $64,000", "CoinDesk", "1h ago"));
        list.add(new News("ETH ETF approved by SEC", "TheBlock", "2h ago"));
        list.add(new News("Solana up 10% after All-Time High", "Decrypt", "2h ago"));
        list.add(new News("NFT sales remain strong: report", "CoinTelegraph", "3h ago"));
        return list;
    }
}
