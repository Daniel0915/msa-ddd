 -- ============================================
  -- 회원 도메인 (Member)
  -- ============================================

  -- 회원 테이블 (소스)
  CREATE TABLE member_member (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL,
      modify_date TIMESTAMP NOT NULL,
      username VARCHAR(255) NOT NULL UNIQUE,
      password VARCHAR(255) NOT NULL,
      nickname VARCHAR(255) NOT NULL,
      activity_score INTEGER NOT NULL DEFAULT 0
  );

  CREATE INDEX idx_member_username ON member_member(username);

  -- ============================================
  -- 게시글 도메인 (Post)
  -- ============================================

  -- 게시글용 회원 복제 테이블
  CREATE TABLE post_member (
      id INTEGER PRIMARY KEY,
      create_date TIMESTAMP NOT NULL,
      modify_date TIMESTAMP NOT NULL,
      username VARCHAR(255) NOT NULL UNIQUE,
      password VARCHAR(255) NOT NULL,
      nickname VARCHAR(255) NOT NULL,
      activity_score INTEGER NOT NULL DEFAULT 0
  );

  -- 게시글 테이블
  CREATE TABLE post_post (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      author_id INTEGER,
      title VARCHAR(255) NOT NULL,
      content TEXT,
      CONSTRAINT fk_post_author FOREIGN KEY (author_id)
          REFERENCES post_member(id) ON DELETE SET NULL
  );

  CREATE INDEX idx_post_author ON post_post(author_id);
  CREATE INDEX idx_post_create_date ON post_post(create_date DESC);

  -- 게시글 댓글 테이블
  CREATE TABLE post_post_comment (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      post_id INTEGER,
      author_id INTEGER,
      content TEXT NOT NULL,
      CONSTRAINT fk_comment_post FOREIGN KEY (post_id)
          REFERENCES post_post(id) ON DELETE CASCADE,
      CONSTRAINT fk_comment_author FOREIGN KEY (author_id)
          REFERENCES post_member(id) ON DELETE SET NULL
  );

  CREATE INDEX idx_comment_post ON post_post_comment(post_id);
  CREATE INDEX idx_comment_author ON post_post_comment(author_id);

  -- ============================================
  -- 캐시 도메인 (Cash)
  -- ============================================

  -- 캐시용 회원 복제 테이블
  CREATE TABLE cash_member (
      id INTEGER PRIMARY KEY,
      create_date TIMESTAMP NOT NULL,
      modify_date TIMESTAMP NOT NULL,
      username VARCHAR(255) NOT NULL UNIQUE,
      password VARCHAR(255) NOT NULL,
      nickname VARCHAR(255) NOT NULL,
      activity_score INTEGER NOT NULL DEFAULT 0
  );

  -- 지갑 테이블
  CREATE TABLE cash_wallet (
      id INTEGER PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      holder_id INTEGER,
      balance BIGINT NOT NULL DEFAULT 0,
      CONSTRAINT fk_wallet_holder FOREIGN KEY (holder_id)
          REFERENCES cash_member(id) ON DELETE CASCADE
  );

  CREATE INDEX idx_wallet_holder ON cash_wallet(holder_id);

  -- 캐시 로그 테이블
  CREATE TABLE cash_cash_log (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      event_type VARCHAR(50) NOT NULL,
      rel_type_code VARCHAR(50),
      rel_id INTEGER,
      holder_id INTEGER,
      wallet_id INTEGER,
      amount BIGINT NOT NULL,
      balance BIGINT NOT NULL,
      CONSTRAINT fk_cashlog_holder FOREIGN KEY (holder_id)
          REFERENCES cash_member(id) ON DELETE SET NULL,
      CONSTRAINT fk_cashlog_wallet FOREIGN KEY (wallet_id)
          REFERENCES cash_wallet(id) ON DELETE CASCADE,
      CONSTRAINT chk_event_type CHECK (event_type IN (
          '충전__무통장입금',
          '충전__PG결제_토스페이먼츠',
          '출금__통장입금',
          '사용__주문결제',
          '임시보관__주문결제',
          '정산지급__상품판매_수수료',
          '정산수령__상품판매_수수료',
          '정산지급__상품판매_대금',
          '정산수령__상품판매_대금'
      ))
  );

  CREATE INDEX idx_cashlog_holder ON cash_cash_log(holder_id);
  CREATE INDEX idx_cashlog_wallet ON cash_cash_log(wallet_id);
  CREATE INDEX idx_cashlog_event_type ON cash_cash_log(event_type);
  CREATE INDEX idx_cashlog_create_date ON cash_cash_log(create_date DESC);

  -- ============================================
  -- 마켓 도메인 (Market)
  -- ============================================

  -- 마켓용 회원 복제 테이블
  CREATE TABLE market_member (
      id INTEGER PRIMARY KEY,
      create_date TIMESTAMP NOT NULL,
      modify_date TIMESTAMP NOT NULL,
      username VARCHAR(255) NOT NULL UNIQUE,
      password VARCHAR(255) NOT NULL,
      nickname VARCHAR(255) NOT NULL,
      activity_score INTEGER NOT NULL DEFAULT 0
  );

  -- 상품 테이블
  CREATE TABLE market_product (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      seller_id INTEGER,
      source_type_code VARCHAR(50),
      source_id INTEGER,
      name VARCHAR(255) NOT NULL,
      description VARCHAR(500),
      price BIGINT NOT NULL,
      sale_price BIGINT NOT NULL,
      CONSTRAINT fk_product_seller FOREIGN KEY (seller_id)
          REFERENCES market_member(id) ON DELETE SET NULL
  );

  CREATE INDEX idx_product_seller ON market_product(seller_id);
  CREATE INDEX idx_product_source ON market_product(source_type_code, source_id);
  CREATE INDEX idx_product_create_date ON market_product(create_date DESC);

  -- 장바구니 테이블
  CREATE TABLE market_cart (
      id INTEGER PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      buyer_id INTEGER,
      items_count INTEGER NOT NULL DEFAULT 0,
      CONSTRAINT fk_cart_buyer FOREIGN KEY (buyer_id)
          REFERENCES market_member(id) ON DELETE CASCADE
  );

  CREATE INDEX idx_cart_buyer ON market_cart(buyer_id);

  -- 장바구니 아이템 테이블
  CREATE TABLE market_cart_item (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      cart_id INTEGER,
      product_id INTEGER,
      CONSTRAINT fk_cartitem_cart FOREIGN KEY (cart_id)
          REFERENCES market_cart(id) ON DELETE CASCADE,
      CONSTRAINT fk_cartitem_product FOREIGN KEY (product_id)
          REFERENCES market_product(id) ON DELETE CASCADE
  );

  CREATE INDEX idx_cartitem_cart ON market_cart_item(cart_id);
  CREATE INDEX idx_cartitem_product ON market_cart_item(product_id);

  -- 주문 테이블
  CREATE TABLE market_order (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      buyer_id INTEGER,
      cancel_date TIMESTAMP,
      request_payment_date TIMESTAMP,
      payment_date TIMESTAMP,
      price BIGINT NOT NULL DEFAULT 0,
      sale_price BIGINT NOT NULL DEFAULT 0,
      CONSTRAINT fk_order_buyer FOREIGN KEY (buyer_id)
          REFERENCES market_member(id) ON DELETE SET NULL
  );

  CREATE INDEX idx_order_buyer ON market_order(buyer_id);
  CREATE INDEX idx_order_create_date ON market_order(create_date DESC);
  CREATE INDEX idx_order_payment_date ON market_order(payment_date DESC);

  -- 주문 아이템 테이블
  CREATE TABLE market_order_item (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      order_id INTEGER,
      product_id INTEGER,
      product_name VARCHAR(255) NOT NULL,
      price BIGINT NOT NULL,
      sale_price BIGINT NOT NULL,
      payout_rate DOUBLE PRECISION NOT NULL DEFAULT 90.0,
      CONSTRAINT fk_orderitem_order FOREIGN KEY (order_id)
          REFERENCES market_order(id) ON DELETE CASCADE,
      CONSTRAINT fk_orderitem_product FOREIGN KEY (product_id)
          REFERENCES market_product(id) ON DELETE SET NULL
  );

  CREATE INDEX idx_orderitem_order ON market_order_item(order_id);
  CREATE INDEX idx_orderitem_product ON market_order_item(product_id);

  -- ============================================
  -- 정산 도메인 (Payout)
  -- ============================================

  -- 정산용 회원 복제 테이블
  CREATE TABLE payout_member (
      id INTEGER PRIMARY KEY,
      create_date TIMESTAMP NOT NULL,
      modify_date TIMESTAMP NOT NULL,
      username VARCHAR(255) NOT NULL UNIQUE,
      password VARCHAR(255) NOT NULL,
      nickname VARCHAR(255) NOT NULL,
      activity_score INTEGER NOT NULL DEFAULT 0
  );

  -- 정산 테이블
  CREATE TABLE payout_payout (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      payee_id INTEGER,
      payout_date TIMESTAMP,
      amount BIGINT NOT NULL DEFAULT 0,
      CONSTRAINT fk_payout_payee FOREIGN KEY (payee_id)
          REFERENCES payout_member(id) ON DELETE SET NULL
  );

  CREATE INDEX idx_payout_payee ON payout_payout(payee_id);
  CREATE INDEX idx_payout_date ON payout_payout(payout_date DESC);

  -- 정산 아이템 테이블
  CREATE TABLE payout_payout_item (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      payout_id INTEGER,
      event_type VARCHAR(50) NOT NULL,
      rel_type_code VARCHAR(50),
      rel_id INTEGER,
      payment_date TIMESTAMP,
      payer_id INTEGER,
      payee_id INTEGER,
      amount BIGINT NOT NULL,
      CONSTRAINT fk_payoutitem_payout FOREIGN KEY (payout_id)
          REFERENCES payout_payout(id) ON DELETE CASCADE,
      CONSTRAINT fk_payoutitem_payer FOREIGN KEY (payer_id)
          REFERENCES payout_member(id) ON DELETE SET NULL,
      CONSTRAINT fk_payoutitem_payee FOREIGN KEY (payee_id)
          REFERENCES payout_member(id) ON DELETE SET NULL,
      CONSTRAINT chk_payout_event_type CHECK (event_type IN (
          '정산__상품판매_수수료',
          '정산__상품판매_대금'
      ))
  );

  CREATE INDEX idx_payoutitem_payout ON payout_payout_item(payout_id);
  CREATE INDEX idx_payoutitem_payer ON payout_payout_item(payer_id);
  CREATE INDEX idx_payoutitem_payee ON payout_payout_item(payee_id);
  CREATE INDEX idx_payoutitem_payment_date ON payout_payout_item(payment_date DESC);

  -- 정산 후보 아이템 테이블
  CREATE TABLE payout_payout_candidate_item (
      id SERIAL PRIMARY KEY,
      create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      modify_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      event_type VARCHAR(50) NOT NULL,
      rel_type_code VARCHAR(50),
      rel_id INTEGER,
      payment_date TIMESTAMP,
      payer_id INTEGER,
      payee_id INTEGER,
      amount BIGINT NOT NULL,
      payout_item_id INTEGER UNIQUE,
      CONSTRAINT fk_payoutcandidate_payer FOREIGN KEY (payer_id)
          REFERENCES payout_member(id) ON DELETE SET NULL,
      CONSTRAINT fk_payoutcandidate_payee FOREIGN KEY (payee_id)
          REFERENCES payout_member(id) ON DELETE SET NULL,
      CONSTRAINT fk_payoutcandidate_payoutitem FOREIGN KEY (payout_item_id)
          REFERENCES payout_payout_item(id) ON DELETE SET NULL,
      CONSTRAINT chk_candidate_event_type CHECK (event_type IN (
          '정산__상품판매_수수료',
          '정산__상품판매_대금'
      ))
  );

  CREATE INDEX idx_payoutcandidate_payer ON payout_payout_candidate_item(payer_id);
  CREATE INDEX idx_payoutcandidate_payee ON payout_payout_candidate_item(payee_id);
  CREATE INDEX idx_payoutcandidate_payment_date ON payout_payout_candidate_item(payment_date DESC);
  CREATE INDEX idx_payoutcandidate_payoutitem ON payout_payout_candidate_item(payout_item_id);

  -- ============================================
  -- 코멘트
  -- ============================================

  COMMENT ON TABLE member_member IS '회원 테이블 (소스)';
  COMMENT ON TABLE post_member IS '게시글 도메인용 회원 복제 테이블';
  COMMENT ON TABLE post_post IS '게시글 테이블';
  COMMENT ON TABLE post_post_comment IS '게시글 댓글 테이블';
  COMMENT ON TABLE cash_member IS '캐시 도메인용 회원 복제 테이블';
  COMMENT ON TABLE cash_wallet IS '지갑 테이블';
  COMMENT ON TABLE cash_cash_log IS '캐시 입출금 로그 테이블';
  COMMENT ON TABLE market_member IS '마켓 도메인용 회원 복제 테이블';
  COMMENT ON TABLE market_product IS '상품 테이블';
  COMMENT ON TABLE market_cart IS '장바구니 테이블';
  COMMENT ON TABLE market_cart_item IS '장바구니 아이템 테이블';
  COMMENT ON TABLE market_order IS '주문 테이블';
  COMMENT ON TABLE market_order_item IS '주문 아이템 테이블';
  COMMENT ON TABLE payout_member IS '정산 도메인용 회원 복제 테이블';
  COMMENT ON TABLE payout_payout IS '정산 테이블';
  COMMENT ON TABLE payout_payout_item IS '정산 아이템 테이블';
  COMMENT ON TABLE payout_payout_candidate_item IS '정산 후보 아이템 테이블 (Batch 처리용)';
