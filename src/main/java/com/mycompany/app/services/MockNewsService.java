package com.mycompany.app.services;

import java.util.ArrayList;
import java.util.List;

import com.mycompany.app.models.News;

public class MockNewsService implements NewsService {
    @Override
    public List<News> getNewsForCrypto(String cryptoId) {
        List<News> list = new ArrayList<>();
        if (cryptoId == null) cryptoId = "";
        String id = cryptoId.trim().toUpperCase();

        // General related headlines (short list)
        list.add(new News(id + " price reacts to macro data", "Reuters", "30m ago"));
        list.add(new News(id + " trading volume spikes amid volatility", "CoinDesk", "1h ago"));

        // Crypto-specific mock headlines
        switch (id) {
            case "BTC":
            case "BTCUSD":
                list.add(new News("BTC passes key resistance at $70k", "Bloomberg", "2h ago"));
                list.add(new News("Miners increase hash rate after difficulty drop", "CoinTelegraph", "3h ago"));
                list.add(new News("Institutional flows into BTC ETFs pick up", "TheBlock", "4h ago"));
                break;
            case "ETH":
            case "ETHUSD":
                list.add(new News("ETH upgrade improves transaction throughput", "Decrypt", "45m ago"));
                list.add(new News("DeFi TVL rises as ETH fees stabilize", "CoinDesk", "2h ago"));
                list.add(new News("Major exchange lists new ETH staking product", "CoinTelegraph", "5h ago"));
                break;
            case "SOL":
            case "SOLANA":
                list.add(new News("Solana validator software update deployed", "TheBlock", "1h ago"));
                list.add(new News("SOL developers announce new NFT marketplace", "Decrypt", "3h ago"));
                break;
            case "ADA":
            case "CARDANO":
                list.add(new News("Cardano smart contract toolkit gains traction", "CoinDesk", "2h ago"));
                break;
            case "DOGE":
            case "DOGECOIN":
                list.add(new News("Dogecoin community campaigns for wider adoption", "CoinTelegraph", "6h ago"));
                break;
            case "XRP":
            case "XRPUSD":
                list.add(new News("XRP sees renewed interest after payment pilot announcement", "Reuters", "50m ago"));
                list.add(new News("Ripple partners with banks for cross-border pilots", "Bloomberg", "3h ago"));
                list.add(new News("XRP liquidity improves on major exchanges", "CoinDesk", "4h ago"));
                break;
            default:
                // Generic crypto-specific mock items
                if (!id.isEmpty()) {
                    list.add(new News(id + " ecosystem updates announced", "CryptoNews", "3h ago"));
                    list.add(new News(id + " developer community publishes roadmap", "DevBlog", "8h ago"));
                }
                break;
        }

        // Recent market summary item
        list.add(new News("Market summary: top cryptos mixed as traders digest news", "MarketWatch", "10m ago"));

        return list;
    }

    @Override
    public List<News> getGeneralNews() {
        List<News> list = new ArrayList<>();
        list.add(new News("BTC hits new high of $64,000", "CoinDesk", "1h ago"));
        list.add(new News("ETH ETF approved by SEC", "TheBlock", "2h ago"));
        list.add(new News("Solana up 10% after All-Time High", "Decrypt", "2h ago"));
        list.add(new News("NFT sales remain strong: report", "CoinTelegraph", "3h ago"));
        list.add(new News("Regulators discuss stablecoin frameworks", "Reuters", "4h ago"));
        list.add(new News("On-chain activity increases after market move", "Glassnode", "30m ago"));
        list.add(new News("Major exchange announces new custody offering", "CoinDesk", "5h ago"));
        list.add(new News("Layer-2 adoption grows as fees fall", "TheBlock", "6h ago"));
        return list;
    }
}
