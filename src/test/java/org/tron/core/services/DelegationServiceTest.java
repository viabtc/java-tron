package org.tron.core.services;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.tron.api.GrpcAPI.BytesMessage;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletGrpc.WalletBlockingStub;
import org.tron.common.application.TronApplicationContext;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.db.Manager;

@Slf4j
public class DelegationServiceTest {

  private DelegationService delegationService;
  private Manager manager;

  public DelegationServiceTest(TronApplicationContext context) {
    delegationService = context.getBean(DelegationService.class);
    manager = context.getBean(Manager.class);
  }

  private void testPay(int cycle) {
    double rate = 0;
    if (cycle == 0) {
      rate = 0.1;
    } else if (cycle == 1) {
      rate = 0.2;
    }
    delegationService.payStandbyWitness();
    Wallet.setAddressPreFixByte(Constant.ADD_PRE_FIX_BYTE_MAINNET);
    byte[] sr1 = Wallet.decodeFromBase58Check("TLTDZBcPoJ8tZ6TTEeEqEvwYFk2wgotSfD");
    long value = manager.getDelegationStore().getReward(cycle, sr1);
    long tmp = 0;
    for (int i = 0; i < 27; i++) {
      tmp += 100000000 + i;
    }
    double d = (double) 16000000 / tmp;
    long expect = (long) (d * 100000026);
    long brokerageAmount = (long) (rate * expect);
    expect -= brokerageAmount;
    Assert.assertEquals(expect, value);
    delegationService.payBlockReward(sr1, 32000000);
    expect += 32000000;
    brokerageAmount = (long) (rate * 32000000);
    expect -= brokerageAmount;
    value = manager.getDelegationStore().getReward(cycle, sr1);
    Assert.assertEquals(expect, value);
  }

  private void testWithdraw() {
    //init
    manager.getDynamicPropertiesStore().saveCurrentCycleNumber(1);
    testPay(1);
    manager.getDynamicPropertiesStore().saveCurrentCycleNumber(2);
    testPay(2);
    byte[] sr1 = Wallet.decodeFromBase58Check("THKJYuUmMKKARNf7s2VT51g5uPY6KEqnat");
    AccountCapsule accountCapsule = manager.getAccountStore().get(sr1);
    byte[] sr27 = Wallet.decodeFromBase58Check("TLTDZBcPoJ8tZ6TTEeEqEvwYFk2wgotSfD");
    accountCapsule.addVotes(ByteString.copyFrom(sr27), 10000000);
    manager.getAccountStore().put(sr1, accountCapsule);
    //
    long value = delegationService.queryReward(sr1);
    long reward1 = (long) ((double) manager.getDelegationStore().getReward(0, sr27) / 100000000
        * 10000000);
    long reward2 = (long) ((double) manager.getDelegationStore().getReward(1, sr27) / 100000000
        * 10000000);
    long reward = reward1 + reward2;
    System.out.println("testWithdraw:" + value + ", reward:" + reward);
    Assert.assertEquals(reward, value);
    delegationService.withdrawReward(sr1, null);
    accountCapsule = manager.getAccountStore().get(sr1);
    long allowance = accountCapsule.getAllowance();
    System.out.println("withdrawReward:" + allowance);
    Assert.assertEquals(reward, allowance);
  }

  public void test() {
    manager.getDynamicPropertiesStore().saveChangeDelegation(1);
    byte[] sr27 = Wallet.decodeFromBase58Check("TLTDZBcPoJ8tZ6TTEeEqEvwYFk2wgotSfD");
    manager.getDelegationStore().setBrokerage(0, sr27, 10);
    manager.getDelegationStore().setBrokerage(1, sr27, 20);
    testPay(0);
    testWithdraw();
  }

  private static String fullnode = "127.0.0.1:50051";

  public static void testGrpc() {
    WalletBlockingStub walletStub = WalletGrpc
        .newBlockingStub(ManagedChannelBuilder.forTarget(fullnode)
            .usePlaintext(true)
            .build());
    BytesMessage.Builder builder = BytesMessage.newBuilder();
    builder.setValue(ByteString.copyFromUtf8("TLTDZBcPoJ8tZ6TTEeEqEvwYFk2wgotSfD"));
    System.out
        .println("getBrokerageInfo: " + walletStub.getBrokerageInfo(builder.build()).getNum());
    System.out.println("getRewardInfo: " + walletStub.getRewardInfo(builder.build()).getNum());
  }

}