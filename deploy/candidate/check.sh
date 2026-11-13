check_candidate() {
    candidate_compose exec -T api curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health |
        python3 -c 'import json,sys; sys.exit(json.load(sys.stdin).get("status") != "UP")'
    local symbol
    for symbol in QQQ SPY; do
        candidate_compose exec -T api curl -fsS --max-time 5 \
            "http://127.0.0.1:8080/api/v1/finance/rooms/$symbol/messages" |
            python3 -c 'import json,sys; x=json.load(sys.stdin); sys.exit(x.get("success") is not True or not isinstance(x.get("data"), list) or any(not isinstance(r,dict) or r.get("symbol")!=sys.argv[1] for r in x["data"]))' "$symbol"
    done
    local code
    code=$(candidate_compose exec -T api curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
        -H 'Content-Type: application/json' --data '{}' \
        http://127.0.0.1:8080/api/v1/finance/rooms/QQQ/messages)
    case "$code" in 401|403) ;; *) return 1 ;; esac
}
