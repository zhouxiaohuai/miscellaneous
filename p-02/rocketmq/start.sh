#!/bin/bash

# RocketMQ 启动脚本 (Podman)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的消息
info() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查 podman-compose 是否安装
check_dependencies() {
    if ! command -v podman-compose &> /dev/null; then
        error "podman-compose 未安装，请先安装:"
        echo "  pip install podman-compose"
        exit 1
    fi
}

# 创建目录结构
create_dirs() {
    info "创建数据目录..."
    mkdir -p data/namesrv/logs
    mkdir -p data/broker/logs
    mkdir -p data/broker/store
}

# 启动服务
start() {
    check_dependencies
    create_dirs

    info "启动 RocketMQ 服务..."
    podman-compose -f podman-compose.yml up -d

    info "等待服务启动..."
    sleep 10

    info "检查服务状态..."
    podman-compose -f podman-compose.yml ps

    echo ""
    info "=== RocketMQ 服务已启动 ==="
    info "NameServer:  localhost:9876"
    info "Broker:      localhost:10911"
    info "Dashboard:   http://localhost:18080"
    echo ""
}

# 停止服务
stop() {
    info "停止 RocketMQ 服务..."
    podman-compose -f podman-compose.yml down
    info "服务已停止"
}

# 查看日志
logs() {
    local service=${1:-"all"}
    if [ "$service" = "all" ]; then
        podman-compose -f podman-compose.yml logs -f
    else
        podman-compose -f podman-compose.yml logs -f "$service"
    fi
}

# 查看状态
status() {
    podman-compose -f podman-compose.yml ps
}

# 清理数据
clean() {
    warn "清理所有数据..."
    read -p "确认清理? (y/N): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        stop
        rm -rf data/*
        info "数据已清理"
    else
        info "取消清理"
    fi
}

# 显示帮助
help() {
    echo "RocketMQ 管理脚本"
    echo ""
    echo "用法: $0 {start|stop|restart|status|logs|clean}"
    echo ""
    echo "命令:"
    echo "  start    - 启动所有服务"
    echo "  stop     - 停止所有服务"
    echo "  restart  - 重启所有服务"
    echo "  status   - 查看服务状态"
    echo "  logs     - 查看日志 (可选: namesrv|broker|dashboard)"
    echo "  clean    - 清理所有数据"
}

# 主逻辑
case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        sleep 2
        start
        ;;
    status)
        status
        ;;
    logs)
        logs "$2"
        ;;
    clean)
        clean
        ;;
    *)
        help
        exit 1
        ;;
esac
