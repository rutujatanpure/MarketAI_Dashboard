import { useEffect, useState } from "react";
import axios from "axios";
import useAuth from "../hooks/useAuth";
import { useNavigate } from "react-router-dom";

export default function Dashboard() {
  const { user, logout, isLoggedIn } = useAuth();
  const [data, setData] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoggedIn) {
      navigate("/"); // redirect to login if not logged in
      return;
    }

    axios
      .get("http://localhost:8080/api/admin/users", {
        headers: { Authorization: `Bearer ${user.token}` },
      })
      .then((res) => setData(res.data))
      .catch((err) => console.error(err));
  }, [isLoggedIn, navigate, user]);

  const handleLogout = () => {
    logout();
    navigate("/");
  };

  return (
    <div>
      <h2>Dashboard</h2>
      <p>Welcome, {user.username}</p>
      <button onClick={handleLogout}>Logout</button>
      <pre>{JSON.stringify(data, null, 2)}</pre>
    </div>
  );
}
