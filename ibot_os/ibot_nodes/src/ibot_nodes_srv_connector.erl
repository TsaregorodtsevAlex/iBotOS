%%%-------------------------------------------------------------------
%%% @author alex
%%% @copyright (C) 2015, <COMPANY>
%%% @doc
%%%
%%% @end
%%% Created : 22. Февр. 2015 21:43
%%%-------------------------------------------------------------------
-module(ibot_nodes_srv_connector).
-author("alex").

-behaviour(gen_server).

%% API
-export([start_link/1, run_node/1]).

%% gen_server callbacks
-export([init/1,
  handle_call/3,
  handle_cast/2,
  handle_info/2,
  terminate/2,
  code_change/3]).

-define(SERVER, ?MODULE).

-include("../../ibot_core/include/debug.hrl").
-include("ibot_comm_commands.hrl").
-include("ibot_nodes_modules.hrl").
-include("nodes_registration_info.hrl").

-record(state, {node_port, node_name}).


start_link([NodeInfo | NodeInfoTopic]) ->
  gen_server:start_link({local, ?SERVER}, ?MODULE, [NodeInfo | NodeInfoTopic], []).


init([NodeInfo | NodeInfoTopic]) -> ?DBG_MODULE_INFO("run nodes: ~p~n", [?MODULE, [NodeInfo | NodeInfoTopic]]),
  ibot_db_srv:add_record(node_registrator_db, 'bar@alex-N550JK', NodeInfo),
  ibot_db_srv:add_record(node_registrator_db, 'bar_topic@alex-N550JK', NodeInfoTopic),
  run_node(NodeInfo),
  %run_node(NodeInfoTopic),
  {ok, #state{node_name = NodeInfo#node_info.nodeNameServer}}.


handle_call({?RESTART_NODE, NodeName}, _From, State) -> ?DBG_MODULE_INFO("handle_call: ~p~n", [?MODULE, [?RESTART_NODE, NodeName]]),
  case gen_server:call(?IBOT_NODES_SRV_REGISTRATOR, {?GET_NODE_INFO, NodeName}) of
    [{NodeName, NodeInfoRecord}] -> ?DBG_MODULE_INFO("handle_call: ~p node found: ~p~n", [?MODULE, [?RESTART_NODE, NodeName], [{NodeName, NodeInfoRecord}]]),
      run_node(NodeInfoRecord); %% Run new node (Restart)
    {response, {ok, NodeInfoRecord}} -> ?DBG_MODULE_INFO("handle_call: ~p node found: ~p~n", [?MODULE, [?RESTART_NODE, NodeName], [{NodeName, NodeInfoRecord}]]),
      run_node(NodeInfoRecord); %% Run new node (Restart)
    [] -> ?DBG_MODULE_INFO("handle_call: ~p node info not found ~n", [?MODULE, [?RESTART_NODE, NodeName]]),
      ok;
    Vals -> ?DBG_MODULE_INFO("handle_call: ~p ~n", [?MODULE, Vals]),
      ok
  end,
  {reply, ok, State};
handle_call(_Request, _From, State) ->
  {reply, ok, State}.


handle_cast(_Request, State) ->
  {noreply, State}.


handle_info({_Port, {data, {eol, "READY!"}}}, State)-> ?DBG_MODULE_INFO("handle_info {eol, READY} start monitor: -> ~n", [?MODULE]),
  ibot_nodes_srv_monitor:start_link(State#state.node_name),
  {noreply, State};
handle_info(Msg, State)-> ?DBG_MODULE_INFO("handle_info(Msg, State) ~p~n", [?MODULE, Msg]),
  {noreply, State}.


terminate(_Reason, _State) ->
  ok.


code_change(_OldVsn, State, _Extra) ->
  {ok, State}.

%%%===================================================================
%%% Internal functions
%%%===================================================================

%% @doc
%% Запуск узла
%% @spec run_node(NodeInfo) -> ok when NodeInfo :: #node_info{}.
%% @end
-spec run_node(NodeInfo) -> ok when NodeInfo :: #node_info{}.
run_node(NodeInfo = #node_info{nodeName = NodeName, nodeServer = NodeServer, nodeNameServer = NodeNameServer,
  nodeLang = NodeLang, nodeExecutable = NodeExecutable,
  nodePreArguments = NodePreArguments, nodePostArguments = NodePostArgumants}) -> ?DBG_MODULE_INFO("run_node(NodeInfo) -> ~p~n", [?MODULE, NodeInfo]),
  %% Проверка наличия исполняющего файла java
  case os:find_executable(NodeExecutable) of
    [] ->
      throw({stop, executable_file_missing});
    ExecutableFile ->
      ArgumentList = lists:append([NodePreArguments, % Аргументы для исполняемого файла
        [NodeName, % Имя запускаемого узла
          % Передаем параметры в узел
          atom_to_list(node()), % Имя текущего узла
          NodeNameServer, % Имя сервера
          erlang:get_cookie()], % Значение Cookies для узла
        NodePostArgumants] % Аргументы определенные пользователем для передачи в узел
      ),
      % Выполянем комманду по запуску узла
      erlang:open_port({spawn_executable, ExecutableFile}, [{line,1000}, stderr_to_stdout, {args, ArgumentList}])
  end.